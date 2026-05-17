import type { ChatEvent, ChatMessage, ChatOptions } from "./types";

type BridgeResponse = {
  askflow_token: string;
  askflow_user_id: string;
  expires_at: string;
};

export class MallChatClient {
  private ws: WebSocket | null = null;
  private listeners = new Set<(e: ChatEvent) => void>();
  private retries = 0;
  private bridgeToken: string | null = null;

  constructor(private readonly opts: ChatOptions) {}

  on(listener: (e: ChatEvent) => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  async connect() {
    this.emit({ type: "connecting" });
    const token = await this.bridge();
    if (!token) return;
    this.openWebSocket(token);
  }

  send(content: string) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      this.emit({ type: "error", error: "socket not open" });
      return;
    }
    const msg: ChatMessage = { role: "user", content, timestamp: Date.now() };
    this.emit({ type: "message", message: msg });
    this.ws.send(JSON.stringify({ type: "user_message", content }));
  }

  close() {
    this.ws?.close();
    this.ws = null;
  }

  private async bridge(): Promise<string | null> {
    const max = this.opts.bridgeRetries ?? 5;
    const mallToken = this.opts.getMallToken();
    if (!mallToken) {
      this.emit({ type: "unauthenticated" });
      return null;
    }
    for (let i = 0; i < max; i++) {
      try {
        const res = await fetch(`${this.opts.mallBaseUrl}/api/v1/integration/auth/bridge`, {
          method: "POST",
          headers: { Authorization: `Bearer ${mallToken}` },
        });
        if (res.status === 401) {
          this.emit({ type: "unauthenticated" });
          return null;
        }
        if (res.status === 404) {
          // user_id_mapping not yet written — wait for UserSyncWorker
          await this.sleep(1000 + i * 500);
          continue;
        }
        if (!res.ok) {
          this.emit({ type: "error", error: `bridge ${res.status}` });
          return null;
        }
        const body: BridgeResponse = await res.json();
        this.bridgeToken = body.askflow_token;
        return body.askflow_token;
      } catch (e) {
        this.emit({ type: "error", error: (e as Error).message });
        await this.sleep(1000);
      }
    }
    this.emit({ type: "error", error: "bridge timeout" });
    return null;
  }

  private openWebSocket(token: string) {
    const wsUrl = this.opts.askflowBaseUrl.replace(/^http/, "ws") + "/api/v1/chat/ws";
    const ws = new WebSocket(wsUrl);
    this.ws = ws;
    ws.addEventListener("open", () => {
      // AskFlow enforces 5s auth-frame timeout after handshake.
      ws.send(JSON.stringify({ type: "auth", token }));
      this.emit({ type: "open" });
      this.retries = 0;
    });
    ws.addEventListener("message", (ev) => {
      try {
        const data = JSON.parse(String(ev.data));
        const msg: ChatMessage = {
          id: data.id,
          role: data.role ?? "assistant",
          content: data.content ?? data.message ?? "",
          timestamp: Date.now(),
        };
        this.emit({ type: "message", message: msg });
      } catch {
        this.emit({ type: "error", error: "invalid frame" });
      }
    });
    ws.addEventListener("error", () => this.emit({ type: "error", error: "ws error" }));
    ws.addEventListener("close", () => {
      this.emit({ type: "closed" });
      this.scheduleReconnect();
    });
  }

  private scheduleReconnect() {
    const backoff = (this.opts.reconnectBackoffMs ?? 1000) * Math.pow(2, this.retries++);
    setTimeout(() => {
      if (this.bridgeToken) this.openWebSocket(this.bridgeToken);
    }, Math.min(backoff, 30_000));
  }

  private emit(event: ChatEvent) {
    for (const l of this.listeners) l(event);
  }

  private sleep(ms: number) {
    return new Promise((r) => setTimeout(r, ms));
  }
}
