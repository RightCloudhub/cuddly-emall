export type ChatOptions = {
  container: HTMLElement;
  mallBaseUrl: string;
  askflowBaseUrl: string;
  getMallToken: () => string | null;
  /** Maximum bridge retries when the mall reports the user isn't synced yet. Default 5. */
  bridgeRetries?: number;
  /** Initial reconnect backoff ms. Default 1000. */
  reconnectBackoffMs?: number;
};

export type ChatMessage = {
  id?: string;
  role: "user" | "assistant" | "system";
  content: string;
  timestamp: number;
};

export type ChatEvent =
  | { type: "connecting" }
  | { type: "open" }
  | { type: "message"; message: ChatMessage }
  | { type: "error"; error: string }
  | { type: "unauthenticated" }
  | { type: "closed" };
