export default function Tickets() {
  return (
    <div>
      <h2 className="text-xl font-semibold mb-4">工单</h2>
      <p className="text-slate-500">
        工单数据由 AskFlow 持有。商城后台通过{" "}
        <code className="bg-slate-100 px-1 rounded">/api/v1/admin/tickets</code>{" "}
        代理至 AskFlow{" "}
        <code className="bg-slate-100 px-1 rounded">GET /api/v1/admin/tickets</code>
        （PR4 集成已就绪，UI 列表在 PR5 后续迭代接入）。
      </p>
    </div>
  );
}
