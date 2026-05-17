import { useEffect, useState } from "react";
import { api } from "../lib/api";

type Row = Awaited<ReturnType<typeof api.listProducts>>[number];

export default function Products() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);

  const refresh = () =>
    api
      .listProducts()
      .then(setRows)
      .catch((e) => setError((e as Error).message));

  useEffect(() => {
    refresh();
  }, []);

  async function toggle(row: Row) {
    try {
      if (row.status === "PUBLISHED") await api.delistProduct(row.id);
      else await api.publishProduct(row.id);
      refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">商品</h2>
      {error && <p className="text-red-600 mb-3">{error}</p>}
      <table className="w-full bg-white rounded shadow border border-slate-200">
        <thead className="bg-slate-100 text-left text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">SPU</th>
            <th className="px-3 py-2">名称</th>
            <th className="px-3 py-2">状态</th>
            <th className="px-3 py-2 w-24">操作</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.spuCode}</td>
              <td className="px-3 py-2">{r.title}</td>
              <td className="px-3 py-2">
                <span
                  className={`px-2 py-0.5 text-xs rounded ${
                    r.status === "PUBLISHED"
                      ? "bg-emerald-100 text-emerald-700"
                      : "bg-slate-100 text-slate-600"
                  }`}
                >
                  {r.status}
                </span>
              </td>
              <td className="px-3 py-2">
                <button
                  className="text-xs px-2 py-1 rounded bg-slate-900 text-white hover:bg-slate-800"
                  onClick={() => toggle(r)}
                >
                  {r.status === "PUBLISHED" ? "下架" : "上架"}
                </button>
              </td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={4} className="px-3 py-6 text-center text-slate-400">
                暂无商品
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
