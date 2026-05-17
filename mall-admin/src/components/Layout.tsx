import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../lib/store";

const links = [
  { to: "/", label: "概览", end: true },
  { to: "/products", label: "商品" },
  { to: "/orders", label: "订单" },
  { to: "/coupons", label: "优惠券" },
  { to: "/tickets", label: "工单" },
];

export default function Layout() {
  const user = useAuth((s) => s.user);
  const clear = useAuth((s) => s.clear);
  const navigate = useNavigate();
  return (
    <div className="min-h-screen flex">
      <aside className="w-56 bg-slate-900 text-slate-100 p-4 flex flex-col gap-2">
        <h1 className="text-lg font-semibold mb-4">Mall Admin</h1>
        <nav className="flex flex-col gap-1">
          {links.map((l) => (
            <NavLink
              key={l.to}
              to={l.to}
              end={l.end}
              className={({ isActive }) =>
                `px-3 py-2 rounded text-sm ${
                  isActive ? "bg-slate-700" : "hover:bg-slate-800"
                }`
              }
            >
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto text-xs text-slate-400">
          <div className="mb-2">{user?.email}</div>
          <button
            className="px-2 py-1 rounded bg-slate-700 hover:bg-slate-600"
            onClick={() => {
              clear();
              navigate("/login");
            }}
          >
            登出
          </button>
        </div>
      </aside>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
