import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: process.env.MALL_BASE_URL || "http://localhost:8080",
    extraHTTPHeaders: { "Content-Type": "application/json" },
    trace: "retain-on-failure",
  },
});
