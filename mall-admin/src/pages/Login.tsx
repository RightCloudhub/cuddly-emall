import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import { useAuth } from "../lib/store";

export default function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const setSession = useAuth((s) => s.setSession);
  const navigate = useNavigate();

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      const res = await api.login(email, password);
      if (res.user.role !== "ADMIN") {
        setError("该账号无管理员权限");
        return;
      }
      setSession(res.token, res.user);
      navigate("/", { replace: true });
    } catch (err) {
      setError((err as Error).message || "登录失败");
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center">
      <form
        className="w-80 bg-white p-6 rounded shadow space-y-4 border border-slate-200"
        onSubmit={submit}
      >
        <h1 className="text-xl font-semibold">Mall Admin 登录</h1>
        <label className="block text-sm">
          邮箱
          <input
            type="email"
            className="mt-1 w-full border rounded px-2 py-1"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label className="block text-sm">
          密码
          <input
            type="password"
            className="mt-1 w-full border rounded px-2 py-1"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        {error && <p className="text-red-600 text-sm">{error}</p>}
        <button
          type="submit"
          className="w-full bg-slate-900 text-white rounded py-2 hover:bg-slate-800"
        >
          登录
        </button>
      </form>
    </div>
  );
}
