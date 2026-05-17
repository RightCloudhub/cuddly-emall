import { useAuth } from "./store";

export type ApiError = { code: string; message: string; details: string[] };

async function request<T>(
  path: string,
  init: RequestInit = {},
  options: { auth?: boolean } = { auth: true },
): Promise<T> {
  const headers = new Headers(init.headers);
  if (!headers.has("Content-Type") && init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (options.auth) {
    const token = useAuth.getState().token;
    if (token) headers.set("Authorization", `Bearer ${token}`);
  }
  const res = await fetch(path, { ...init, headers });
  if (res.status === 401) {
    useAuth.getState().clear();
  }
  if (!res.ok) {
    let body: ApiError | string;
    try {
      body = (await res.json()) as ApiError;
    } catch {
      body = await res.text();
    }
    throw Object.assign(new Error(typeof body === "string" ? body : body.message), {
      status: res.status,
      body,
    });
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const api = {
  login: (email: string, password: string) =>
    request<{ token: string; user: { id: number; username: string; email: string; role: string } }>(
      "/api/v1/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) },
      { auth: false },
    ),

  // Catalog
  listProducts: () =>
    request<{ id: number; spuCode: string; title: string; status: string }[]>(
      "/api/v1/admin/products",
    ),
  publishProduct: (id: number) =>
    request<unknown>(`/api/v1/admin/products/${id}/publish`, { method: "POST" }),
  delistProduct: (id: number) =>
    request<unknown>(`/api/v1/admin/products/${id}/delist`, { method: "POST" }),

  // Orders
  listOrders: () =>
    request<{ id: number; orderNo: string; status: string; total: string; userId: number }[]>(
      "/api/v1/admin/orders",
    ),
  shipOrder: (id: number) =>
    request<unknown>(`/api/v1/admin/orders/${id}/ship`, { method: "POST" }),

  // Coupons
  listCoupons: () =>
    request<
      {
        id: number;
        code: string;
        type: "FLAT" | "PERCENT";
        value: string;
        minTotal: string;
        enabled: boolean;
      }[]
    >("/api/v1/admin/coupons"),
  createCoupon: (body: {
    code: string;
    type: "FLAT" | "PERCENT";
    value: string;
    minTotal?: string;
  }) =>
    request<unknown>("/api/v1/admin/coupons", { method: "POST", body: JSON.stringify(body) }),
};
