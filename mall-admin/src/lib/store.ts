import { create } from "zustand";

type AuthState = {
  token: string | null;
  user: { id: number; username: string; email: string; role: string } | null;
  setSession: (token: string, user: AuthState["user"]) => void;
  clear: () => void;
};

const TOKEN_KEY = "mall.admin.token";
const USER_KEY = "mall.admin.user";

export const useAuth = create<AuthState>((set) => ({
  token: localStorage.getItem(TOKEN_KEY),
  user: (() => {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  })(),
  setSession: (token, user) => {
    localStorage.setItem(TOKEN_KEY, token);
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user));
    set({ token, user });
  },
  clear: () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    set({ token: null, user: null });
  },
}));
