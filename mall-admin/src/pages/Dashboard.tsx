import { useEffect, useState } from "react";
import { api } from "../lib/api";

export default function Dashboard() {
  const [stats, setStats] = useState<{ products: number; orders: number; coupons: number }>({
    products: 0,
    orders: 0,
    coupons: 0,
  });
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.listProducts(), api.listOrders(), api.listCoupons()])
      .then(([p, o, c]) => setStats({ products: p.length, orders: o.length, coupons: c.length }))
      .catch((e) => setError((e as Error).message));
  }, []);

  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">概览</h2>
      {error && <p className="text-red-600 mb-4">{error}</p>}
      <div className="grid grid-cols-3 gap-4 max-w-2xl">
        <Card label="商品数" value={stats.products} />
        <Card label="订单数" value={stats.orders} />
        <Card label="优惠券" value={stats.coupons} />
      </div>
    </div>
  );
}

function Card({ label, value }: { label: string; value: number }) {
  return (
    <div className="bg-white p-4 rounded shadow border border-slate-200">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="text-2xl font-semibold mt-1">{value}</div>
    </div>
  );
}
