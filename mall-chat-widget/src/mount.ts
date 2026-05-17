import { MallChatClient } from "./client";
import type { ChatMessage, ChatOptions } from "./types";

/**
 * Mount a minimal chat UI into {@code opts.container}. The DOM is intentionally unstyled — host
 * pages should override classes or skip {@code mountMallChat} entirely and use
 * {@link MallChatClient} directly for custom UIs.
 */
export function mountMallChat(opts: ChatOptions) {
  const client = new MallChatClient(opts);

  const root = opts.container;
  root.innerHTML = "";
  root.classList.add("mall-chat");

  const status = document.createElement("div");
  status.className = "mall-chat-status";
  status.textContent = "连接中…";

  const list = document.createElement("ul");
  list.className = "mall-chat-messages";

  const form = document.createElement("form");
  form.className = "mall-chat-form";
  const input = document.createElement("input");
  input.placeholder = "请输入您的问题…";
  input.disabled = true;
  const send = document.createElement("button");
  send.type = "submit";
  send.textContent = "发送";
  send.disabled = true;
  form.append(input, send);

  root.append(status, list, form);

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const text = input.value.trim();
    if (!text) return;
    client.send(text);
    input.value = "";
  });

  client.on((ev) => {
    switch (ev.type) {
      case "connecting":
        status.textContent = "连接中…";
        break;
      case "open":
        status.textContent = "已连接";
        input.disabled = false;
        send.disabled = false;
        break;
      case "message":
        list.append(renderMessage(ev.message));
        list.scrollTop = list.scrollHeight;
        break;
      case "error":
        status.textContent = `错误: ${ev.error}`;
        break;
      case "unauthenticated":
        status.textContent = "请先登录商城";
        input.disabled = true;
        send.disabled = true;
        break;
      case "closed":
        status.textContent = "连接已关闭，正在重连…";
        input.disabled = true;
        send.disabled = true;
        break;
    }
  });

  client.connect();
  return client;
}

function renderMessage(m: ChatMessage): HTMLLIElement {
  const li = document.createElement("li");
  li.className = `mall-chat-message mall-chat-${m.role}`;
  li.textContent = m.content;
  return li;
}
