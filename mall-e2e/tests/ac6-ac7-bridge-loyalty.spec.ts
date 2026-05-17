import { expect, test } from "@playwright/test";
import { env, mallRequest, registerMallUser, uniq, waitForCondition } from "./helpers";

/**
 * AC6 (chat widget bridge): mall user already logged in → no second login → bridge mints AskFlow
 * JWT signed with the shared secret. The widget itself is browser-only; here we exercise just the
 * bridge handshake the widget depends on.
 *
 * AC7 (loyalty query): once mapping exists, the service-token-only loyalty endpoint returns a row.
 */
test.describe("AC6/AC7 — chat bridge + loyalty", () => {
  test("bridge returns valid askflow_token + loyalty default row", async () => {
    test.skip(!env.SERVICE_TOKEN, "MALL_ASKFLOW_SERVICE_TOKEN not set");
    const mall = await mallRequest();

    const { username, email } = uniq("ac67");
    const reg = await registerMallUser(mall, username, email);
    await waitForCondition(async () => {
      const r = await mall.post("/api/v1/integration/auth/bridge", {
        headers: { Authorization: `Bearer ${reg.token}` },
      });
      return r.status() === 200;
    });

    const bridge = await mall
      .post("/api/v1/integration/auth/bridge", {
        headers: { Authorization: `Bearer ${reg.token}` },
      })
      .then((r) => r.json());
    expect(bridge.askflow_token).toBeTruthy();

    // AC7: loyalty endpoint (service-token-only).
    const loyalty = await mall.get(
      `/api/v1/integration/loyalty/points?askflow_user_id=${bridge.askflow_user_id}`,
      { headers: { Authorization: `Bearer ${env.SERVICE_TOKEN}` } },
    );
    expect(loyalty.ok()).toBeTruthy();
    const loyaltyBody = await loyalty.json();
    expect(loyaltyBody.user_id).toBe(String(reg.user.id));
    expect(loyaltyBody.points).toBeGreaterThanOrEqual(0);
    expect(["BRONZE", "SILVER", "GOLD", "PLATINUM"]).toContain(loyaltyBody.tier);
  });
});
