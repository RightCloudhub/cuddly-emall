import { expect, test } from "@playwright/test";
import {
  askflowRequest,
  env,
  mallRequest,
  registerMallUser,
  uniq,
  waitForCondition,
} from "./helpers";

/**
 * AC1: 商城注册新用户 → (≤30s) AskFlow 能看到该用户 → user_id_mapping 落表.
 *
 * Verifies the I5 (user sync outbox) flow end-to-end: the mall registers a user, the
 * UserSyncWorker drains the outbox into AskFlow's POST /api/v1/admin/users, and the auth bridge
 * starts returning 200 once user_id_mapping has been written.
 */
test("AC1 — registering a user populates user_id_mapping within 30s", async () => {
  const mall = await mallRequest();
  const { username, email } = uniq("ac1");
  const reg = await registerMallUser(mall, username, email);

  await waitForCondition(
    async () => {
      const res = await mall.post("/api/v1/integration/auth/bridge", {
        headers: { Authorization: `Bearer ${reg.token}` },
      });
      // 200 means the bridge found the mapping. 404 means worker hasn't drained yet.
      return res.status() === 200;
    },
    {
      timeoutMs: 30_000,
      message: "user_id_mapping not written within 30s — UserSyncWorker stalled",
    },
  );

  // Sanity: bridge returns an AskFlow token usable against AskFlow's WS authentication.
  const bridge = await mall.post("/api/v1/integration/auth/bridge", {
    headers: { Authorization: `Bearer ${reg.token}` },
  });
  const body = await bridge.json();
  expect(body.askflow_token).toBeTruthy();
  expect(body.askflow_user_id).toMatch(/^[0-9a-f-]{36}$/);

  if (env.SERVICE_TOKEN) {
    // Optional: confirm AskFlow itself recognizes the new user via admin tickets endpoint.
    const askflow = await askflowRequest();
    const listed = await askflow.get(
      `/api/v1/admin/tickets?user_id=${body.askflow_user_id}`,
      { headers: { Authorization: `Bearer ${env.SERVICE_TOKEN}` } },
    );
    expect([200, 404]).toContain(listed.status());
  }
});
