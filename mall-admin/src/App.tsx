import { Navigate, Route, Routes } from "react-router-dom";
import Layout from "./components/Layout";
import { useAuth } from "./lib/store";
import Coupons from "./pages/Coupons";
import Dashboard from "./pages/Dashboard";
import Login from "./pages/Login";
import Orders from "./pages/Orders";
import Products from "./pages/Products";
import Tickets from "./pages/Tickets";

function Protected({ children }: { children: React.ReactNode }) {
  const token = useAuth((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route
        element={
          <Protected>
            <Layout />
          </Protected>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="products" element={<Products />} />
        <Route path="orders" element={<Orders />} />
        <Route path="coupons" element={<Coupons />} />
        <Route path="tickets" element={<Tickets />} />
      </Route>
    </Routes>
  );
}
