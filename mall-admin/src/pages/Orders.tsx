import { useEffect, useState } from "react";
import { api } from "../lib/api";

type Row = Awaited<ReturnType<typeof api.listOrders>>[number];

export default function Orders() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = () =>
    api
      .listOrders()
      .then(setRows)
      .catch((e) => setError((e as Error).message));

  useEffect(() => {
    refresh();
  }, []);

  async function ship(id: number) {
    try {
      await api.shipOrder(id);
      refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">订单</h2>
      {error && <p className="text-red-600 mb-3">{error}</p>}
      <table className="w-full bg-white rounded shadow border border-slate-200">
        <thead className="bg-slate-100 text-left text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">订单号</th>
            <th className="px-3 py-2">用户</th>
            <th className="px-3 py-2">状态</th>
            <th className="px-3 py-2">金额</th>
            <th className="px-3 py-2 w-24">操作</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.orderNo}</td>
              <td className="px-3 py-2">{r.userId}</td>
              <td className="px-3 py-2 text-xs">{r.status}</td>
              <td className="px-3 py-2">{r.total}</td>
              <td className="px-3 py-2">
                {r.status === "PAID" && (
                  <button
                    className="text-xs px-2 py-1 rounded bg-slate-900 text-white hover:bg-slate-800"
                    onClick={() => ship(r.id)}
                  >
                    发货
                  </button>
                )}
              </td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={5} className="px-3 py-6 text-center text-slate-400">
                暂无订单
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
