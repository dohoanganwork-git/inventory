import { Avatar, Button, Drawer, Dropdown, Grid, Layout, Menu, Space, Typography } from 'antd';
import type { MenuProps } from 'antd';
import {
  AppstoreOutlined, BarChartOutlined, DashboardOutlined, DatabaseOutlined, DollarOutlined, FileDoneOutlined,
  ImportOutlined, ExportOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, MenuOutlined, ShopOutlined,
  TagsOutlined, TeamOutlined, UserOutlined, WarningOutlined, AuditOutlined, KeyOutlined, ReconciliationOutlined,
} from '@ant-design/icons';
import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import type { Role } from '../api/types';
import NotificationBell from './NotificationBell';
import { roleLabel } from '../utils/labels';

const { Sider, Header, Content } = Layout;

interface NavItem { key: string; label: string; icon: JSX.Element; roles?: Role[] }
interface NavGroup { group: string; items: NavItem[] }

const NAV: NavGroup[] = [
  { group: '', items: [{ key: '/', label: 'Tổng quan', icon: <DashboardOutlined /> }] },
  { group: 'Danh mục', items: [
    { key: '/mat-hang', label: 'Mặt hàng', icon: <AppstoreOutlined /> },
    { key: '/loai-hang', label: 'Loại hàng', icon: <TagsOutlined /> },
    { key: '/nha-cung-cap', label: 'Nhà cung cấp & hợp đồng', icon: <ShopOutlined /> },
  ] },
  { group: 'Kho hàng', items: [
    { key: '/ton-kho', label: 'Tồn kho', icon: <DatabaseOutlined /> },
    { key: '/phieu-nhap', label: 'Phiếu nhập hàng', icon: <ImportOutlined /> },
    { key: '/phieu-xuat', label: 'Doanh số POS / Phiếu xuất', icon: <ExportOutlined /> },
    { key: '/kiem-ke', label: 'Kiểm kê & đối soát kho', icon: <AuditOutlined /> },
    { key: '/canh-bao', label: 'Cảnh báo tồn kho', icon: <WarningOutlined />, roles: ['MANAGER', 'STAFF'] },
  ] },
  { group: 'Công nợ', items: [
    { key: '/cong-no', label: 'Công nợ nhà cung cấp', icon: <DollarOutlined />, roles: ['MANAGER', 'ACCOUNTANT'] },
    { key: '/doi-soat-cong-no', label: 'Đối soát công nợ', icon: <ReconciliationOutlined />, roles: ['MANAGER', 'ACCOUNTANT'] },
  ] },
  { group: 'Báo cáo', items: [{ key: '/bao-cao', label: 'Báo cáo thống kê', icon: <BarChartOutlined /> }] },
  { group: 'Hệ thống', items: [{ key: '/nguoi-dung', label: 'Tài khoản người dùng', icon: <TeamOutlined />, roles: ['MANAGER'] }] },
];

function Brand({ collapsed }: { collapsed?: boolean }) {
  return (
    <div className="brand">
      <div className="brand-logo"><FileDoneOutlined /></div>
      {!collapsed && (
        <div className="brand-text">
          <div className="brand-name">LongWaits Kho</div>
          <div className="brand-sub">Công ty TNHH Sông Đêm</div>
        </div>
      )}
    </div>
  );
}

export default function AppLayout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const screens = Grid.useBreakpoint();
  const mobile = !screens.lg;
  const [collapsed, setCollapsed] = useState(false);
  const [drawer, setDrawer] = useState(false);

  const items: MenuProps['items'] = NAV.map((g) => {
    const visible = g.items.filter((i) => !i.roles || (user && i.roles.includes(user.role)));
    if (!visible.length) return null;
    const children = visible.map((i) => ({ key: i.key, icon: i.icon, label: i.label }));
    return g.group ? { type: 'group' as const, key: g.group, label: g.group, children } : children[0];
  }).filter(Boolean) as MenuProps['items'];

  const allKeys = NAV.flatMap((g) => g.items.map((i) => i.key));
  const selected = allKeys.filter((k) => (k === '/' ? loc.pathname === '/' : loc.pathname.startsWith(k)))
    .sort((a, b) => b.length - a.length)[0] ?? '/';

  const menu = (
    <Menu theme="dark" mode="inline" selectedKeys={[selected]} items={items}
      onClick={(e) => { nav(e.key); setDrawer(false); }} />
  );

  const initials = (user?.fullName ?? '?').split(' ').slice(-1)[0].charAt(0).toUpperCase();
  const userMenu: MenuProps['items'] = [
    { key: 'profile', icon: <UserOutlined />, label: 'Thông tin cá nhân', onClick: () => nav('/tai-khoan') },
    { key: 'password', icon: <KeyOutlined />, label: 'Đổi mật khẩu', onClick: () => nav('/tai-khoan?tab=mat-khau') },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true, onClick: () => { logout(); nav('/dang-nhap'); } },
  ];

  return (
    <Layout className="app-shell">
      {!mobile && (
        <Sider width={248} collapsed={collapsed} collapsedWidth={72} className="app-sider" trigger={null}>
          <Brand collapsed={collapsed} />
          {menu}
        </Sider>
      )}
      {mobile && (
        <Drawer placement="left" open={drawer} onClose={() => setDrawer(false)} width={268} closable={false}
          styles={{ body: { padding: 0, background: '#0f1b33' } }}>
          <Brand />
          {menu}
        </Drawer>
      )}
      <Layout>
        <Header className="app-header">
          <Button type="text" aria-label="Mở/đóng menu"
            icon={mobile ? <MenuOutlined /> : collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => (mobile ? setDrawer(true) : setCollapsed(!collapsed))} />
          <div className="header-title">{mobile ? 'LongWaits Kho' : 'Quản lý tồn kho & nhà cung cấp · LongWaits Jazz Club'}</div>
          <Space size={8}>
            <NotificationBell />
            <Dropdown menu={{ items: userMenu }} trigger={['click']} placement="bottomRight">
              <Button type="text" className="user-button">
                <Avatar size={32} style={{ background: '#2a78d6' }}>{initials}</Avatar>
                {screens.md && (
                  <span className="user-meta">
                    <Typography.Text strong className="user-name">{user?.fullName}</Typography.Text>
                    <span className="user-role">{roleLabel[user?.role ?? '']}</span>
                  </span>
                )}
              </Button>
            </Dropdown>
          </Space>
        </Header>
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
