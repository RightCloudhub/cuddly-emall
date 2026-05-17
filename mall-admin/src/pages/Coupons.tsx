import { FormEvent, useEffect, useState } from "react";
import { api } from "../lib/api";

type Row = Awaited<ReturnType<typeof api.listCoupons>>[number];

export default function Coupons() {
  const [rows, setRows] = useState<Row[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({ code: "", type: "FLAT" as "FLAT" | "PERCENT", value: "", minTotal: "" });

  const refresh = () =>
    api
      .listCoupons()
      .then(setRows)
      .catch((e) => setError((e as Error).message));

  useEffect(() => {
    refresh();
  }, []);

  async function create(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api.createCoupon({
        code: form.code,
        type: form.type,
        value: form.value,
        minTotal: form.minTotal || undefined,
      });
      setForm({ code: "", type: "FLAT", value: "", minTotal: "" });
      refresh();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">优惠券</h2>
      {error && <p className="text-red-600 mb-3">{error}</p>}
      <form
        onSubmit={create}
        className="bg-white p-4 rounded shadow border border-slate-200 mb-6 grid grid-cols-5 gap-2 items-end"
      >
        <Field label="码">
          <input
            className="border rounded px-2 py-1 w-full"
            value={form.code}
            onChange={(e) => setForm({ ...form, code: e.target.value })}
            required
          />
        </Field>
        <Field label="类型">
          <select
            className="border rounded px-2 py-1 w-full"
            value={form.type}
            onChange={(e) => setForm({ ...form, type: e.target.value as "FLAT" | "PERCENT" })}
          >
            <option value="FLAT">满减 (FLAT)</option>
            <option value="PERCENT">折扣 (PERCENT)</option>
          </select>
        </Field>
        <Field label={form.type === "PERCENT" ? "百分比" : "减免金额"}>
          <input
            className="border rounded px-2 py-1 w-full"
            value={form.value}
            onChange={(e) => setForm({ ...form, value: e.target.value })}
            required
          />
        </Field>
        <Field label="最低消费">
          <input
            className="border rounded px-2 py-1 w-full"
            value={form.minTotal}
            onChange={(e) => setForm({ ...form, minTotal: e.target.value })}
            placeholder="0"
          />
        </Field>
        <button
          type="submit"
          className="bg-slate-900 text-white rounded px-3 py-2 hover:bg-slate-800"
        >
          新增
        </button>
      </form>

      <table className="w-full bg-white rounded shadow border border-slate-200">
        <thead className="bg-slate-100 text-left text-xs uppercase text-slate-500">
          <tr>
            <th className="px-3 py-2">码</th>
            <th className="px-3 py-2">类型</th>
            <th className="px-3 py-2">值</th>
            <th className="px-3 py-2">最低消费</th>
            <th className="px-3 py-2">状态</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-t border-slate-100">
              <td className="px-3 py-2 font-mono text-xs">{r.code}</td>
              <td className="px-3 py-2">{r.type}</td>
              <td className="px-3 py-2">{r.value}</td>
              <td className="px-3 py-2">{r.minTotal}</td>
              <td className="px-3 py-2 text-xs">{r.enabled ? "启用" : "停用"}</td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={5} className="px-3 py-6 text-center text-slate-400">
                暂无优惠券
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block text-xs text-slate-500">
      {label}
      {children}
    </label>
  );
}
