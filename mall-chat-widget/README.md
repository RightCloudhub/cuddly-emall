# @mall/chat-widget

Embeddable customer-support chat widget for the mall storefront. Authenticates with the mall via
the user's mall JWT, swaps it for an AskFlow JWT through the mall auth bridge
(`POST /api/v1/integration/auth/bridge`), and opens a WebSocket directly to AskFlow's
`/api/v1/chat/ws` endpoint.

The widget is intentionally minimal — it exposes a `mountMallChat(opts)` function that mounts a
container element with `<input>` / message list, and provides a small `MallChatClient` API for
projects that want to render their own UI.

## Usage

```ts
import { mountMallChat } from "@mall/chat-widget";

mountMallChat({
  container: document.getElementById("chat-root")!,
  mallBaseUrl: "https://mall.example.com",
  askflowBaseUrl: "https://askflow.example.com",
  // The mall JWT — read from your auth store / cookie.
  getMallToken: () => localStorage.getItem("mall.token"),
});
```

The widget polls the bridge endpoint once at mount; on `404` (user not yet synced to AskFlow) it
retries with 1s backoff up to 5 times. On `401` it surfaces an `unauthenticated` event so the host
page can redirect to login.
