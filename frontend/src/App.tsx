import { Result, Button, Spin } from 'antd';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from './auth/AuthContext';
import type { Role } from './api/types';
import AppLayout from './components/AppLayout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ProfilePage from './pages/ProfilePage';
import UsersPage from './pages/UsersPage';
import CategoriesPage from './pages/CategoriesPage';
import ItemsPage from './pages/ItemsPage';
import SuppliersPage from './pages/SuppliersPage';
import InventoryPage from './pages/InventoryPage';
import ReceiptsPage from './pages/ReceiptsPage';
import ReceiptFormPage from './pages/ReceiptFormPage';
import ReceiptDetailPage from './pages/ReceiptDetailPage';
import SalesPage from './pages/SalesPage';
import SalesImportPage from './pages/SalesImportPage';
import SalesDetailPage from './pages/SalesDetailPage';
import DebtsPage from './pages/DebtsPage';
import DebtReconListPage from './pages/DebtReconListPage';
import DebtReconDetailPage from './pages/DebtReconDetailPage';
import StocktakesPage from './pages/StocktakesPage';
import StocktakeDetailPage from './pages/StocktakeDetailPage';
import AlertsPage from './pages/AlertsPage';
import ReportsPage from './pages/ReportsPage';

function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const loc = useLocation();
  if (loading) return <div className="full-center"><Spin size="large" /></div>;
  if (!user) return <Navigate to="/dang-nhap" replace state={{ from: loc.pathname }} />;
  return <>{children}</>;
}

function Guard({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { hasRole } = useAuth();
  const nav = useNavigate();
  if (!hasRole(...roles)) {
    return <Result status="403" title="Không có quyền truy cập" subTitle="Chức năng này không thuộc quyền của vai trò hiện tại."
      extra={<Button type="primary" onClick={() => nav('/')}>Về trang tổng quan</Button>} />;
  }
  return <>{children}</>;
}

export default function App() {
  const nav = useNavigate();
  return (
    <Routes>
      <Route path="/dang-nhap" element={<LoginPage />} />
      <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
        <Route index element={<DashboardPage />} />
        <Route path="tai-khoan" element={<ProfilePage />} />
        <Route path="nguoi-dung" element={<Guard roles={['MANAGER']}><UsersPage /></Guard>} />
        <Route path="loai-hang" element={<CategoriesPage />} />
        <Route path="mat-hang" element={<ItemsPage />} />
        <Route path="nha-cung-cap" element={<SuppliersPage />} />
        <Route path="ton-kho" element={<InventoryPage />} />
        <Route path="phieu-nhap" element={<ReceiptsPage />} />
        <Route path="phieu-nhap/tao" element={<Guard roles={['STAFF', 'MANAGER']}><ReceiptFormPage /></Guard>} />
        <Route path="phieu-nhap/:id/sua" element={<Guard roles={['MANAGER']}><ReceiptFormPage /></Guard>} />
        <Route path="phieu-nhap/:id" element={<ReceiptDetailPage />} />
        <Route path="phieu-xuat" element={<SalesPage />} />
        <Route path="phieu-xuat/nhap-pos" element={<Guard roles={['MANAGER']}><SalesImportPage /></Guard>} />
        <Route path="phieu-xuat/:id" element={<SalesDetailPage />} />
        <Route path="cong-no" element={<Guard roles={['MANAGER', 'ACCOUNTANT']}><DebtsPage /></Guard>} />
        <Route path="doi-soat-cong-no" element={<Guard roles={['MANAGER', 'ACCOUNTANT']}><DebtReconListPage /></Guard>} />
        <Route path="doi-soat-cong-no/:id" element={<Guard roles={['MANAGER', 'ACCOUNTANT']}><DebtReconDetailPage /></Guard>} />
        <Route path="kiem-ke" element={<StocktakesPage />} />
        <Route path="kiem-ke/:id" element={<StocktakeDetailPage />} />
        <Route path="canh-bao" element={<Guard roles={['MANAGER', 'STAFF']}><AlertsPage /></Guard>} />
        <Route path="bao-cao" element={<ReportsPage />} />
        <Route path="*" element={<Result status="404" title="Không tìm thấy trang"
          extra={<Button type="primary" onClick={() => nav('/')}>Về trang tổng quan</Button>} />} />
      </Route>
    </Routes>
  );
}
