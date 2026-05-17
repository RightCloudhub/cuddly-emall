import { expect, test } from "@playwright/test";
import { env, mallRequest, registerMallUser, uniq, waitForCondition } from "./helpers";

/**
 * AC2: 商城下单 MO... → 立即在 AskFlow 客服问"我的订单 MO..." → 返回真实订单状态.
 *
 * We test the mall-facing half: the I1 order lookup webhook returns a real, well-shaped record
 * for an order the mall just placed. The "ask AskFlow in chat" half is exercised in PR5 via
 * manual smoke (and validated in AC5 below).
 */
test("AC2 — placing an order makes it queryable via the integration order-lookup webhook", async () => {
  test.skip(!env.SERVICE_TOKEN, "MALL_ASKFLOW_SERVICE_TOKEN not set");
  const mall = await mallRequest();

  // Register, wait for sync, place an order. The test relies on pre-existing seed data:
  // an admin account + a published SKU. Seed scripts live in docker-compose.mall.yml init.
  const adminEmail = process.env.MALL_ADMIN_EMAIL;
  const adminPassword = process.env.MALL_ADMIN_PASSWORD;
  test.skip(!adminEmail || !adminPassword, "MALL_ADMIN_EMAIL/PASSWORD not provided");

  const { username, email } = uniq("ac2");
  const reg = await registerMallUser(mall, username, email);

  await waitForCondition(async () => {
    const res = await mall.post("/api/v1/integration/auth/bridge", {
      headers: { Authorization: `Bearer ${reg.token}` },
    });
    return res.status() === 200;
  });

  // Create an address.
  const addr = await mall.post("/api/v1/users/me/addresses", {
    headers: { Authorization: `Bearer ${reg.token}` },
    data: {
      recipient: "Alice",
      phone: "13800001111",
      province: "Shanghai",
      city: "Shanghai",
      district: "Pudong",
      detail: "100 Test Rd",
      isDefault: true,
    },
  });
  expect(addr.ok()).toBeTruthy();
  const address = await addr.json();

  // Browse the first published product / variant — assume seed data exposes at least one.
  const products = await mall.get("/api/v1/products");
  expect(products.ok()).toBeTruthy();
  const productList = await products.json();
  test.skip(!Array.isArray(productList) || productList.length === 0, "no published products in seed");
  const product = await mall
    .get(`/api/v1/products/${productList[0].id}`)
    .then((r) => r.json());
  const skuId = product.variants?.[0]?.id;
  test.skip(!skuId, "first product has no variant in seed");

  // Place an order.
  const placed = await mall.post("/api/v1/orders", {
    headers: { Authorization: `Bearer ${reg.token}` },
    data: {
      addressId: address.id,
      items: [{ skuId, qty: 1 }],
    },
  });
  expect(placed.ok(), `place order failed: ${placed.status()} ${await placed.text()}`).toBeTruthy();
  const placedBody = await placed.json();
  const orderNo: string = placedBody.order.orderNo;
  expect(orderNo).toMatch(/^MO\d{12}$/);

  // I1: AskFlow → mall lookup.
  const lookup = await mall.get(`/api/v1/integration/orders/lookup?order_id=${orderNo}`, {
    headers: { Authorization: `Bearer ${env.SERVICE_TOKEN}` },
  });
  expect(lookup.ok()).toBeTruthy();
  const looked = await lookup.json();
  expect(looked.order_id).toBe(orderNo);
  expect(looked.items.length).toBeGreaterThan(0);
});
