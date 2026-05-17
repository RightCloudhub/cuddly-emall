import { APIRequestContext, expect, request } from "@playwright/test";

const MALL_BASE = process.env.MALL_BASE_URL || "http://localhost:8080";
const ASKFLOW_BASE = process.env.ASKFLOW_BASE_URL || "http://localhost:8000";
const SERVICE_TOKEN = process.env.MALL_ASKFLOW_SERVICE_TOKEN || "";

export type RegisterResult = {
  token: string;
  user: { id: number; username: string; email: string; role: string };
};

export function uniq(prefix: string): { username: string; email: string } {
  const tag = `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
  return { username: tag, email: `${tag}@example.com` };
}

export async function mallRequest(): Promise<APIRequestContext> {
  return await request.newContext({ baseURL: MALL_BASE });
}

export async function askflowRequest(): Promise<APIRequestContext> {
  return await request.newContext({ baseURL: ASKFLOW_BASE });
}

export async function registerMallUser(
  ctx: APIRequestContext,
  username: string,
  email: string,
  password = "password123",
): Promise<RegisterResult> {
  const res = await ctx.post("/api/v1/auth/register", {
    data: { username, email, password },
  });
  expect(res.ok(), `register failed: ${res.status()} ${await res.text()}`).toBeTruthy();
  return (await res.json()) as RegisterResult;
}

export async function waitForCondition(
  fn: () => Promise<boolean>,
  opts: { timeoutMs?: number; intervalMs?: number; message?: string } = {},
): Promise<void> {
  const timeout = opts.timeoutMs ?? 30_000;
  const interval = opts.intervalMs ?? 1000;
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await fn()) return;
    await new Promise((r) => setTimeout(r, interval));
  }
  throw new Error(opts.message ?? "waitForCondition timed out");
}

export const env = { MALL_BASE, ASKFLOW_BASE, SERVICE_TOKEN };
