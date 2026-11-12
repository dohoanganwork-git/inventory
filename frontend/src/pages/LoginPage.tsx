import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { LockOutlined, UserOutlined, CheckCircleOutlined } from '@ant-design/icons';
import logoWhite from '../assets/logo-longwaits-white.png';
import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { parseError } from '../api/client';

export default function LoginPage() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (user) return <Navigate to={(loc.state as { from?: string })?.from ?? '/'} replace />;

  const submit = async (v: { username: string; password: string }) => {
    setLoading(true);
    setError(null);
    try {
      await login(v.username.trim(), v.password);
      nav((loc.state as { from?: string })?.from ?? '/', { replace: true });
    } catch (e) {
      setError((await parseError(e)).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-intro">
        <img src={logoWhite} alt="LongWaits" className="login-logo" />
        <h1>Quản lý tồn kho &amp; nhà cung cấp</h1>
        <p>Công ty TNHH Sông Đêm – cơ sở LongWaits Jazz Club, số 5 Nguyễn Quang Bích, Hoàn Kiếm, Hà Nội.</p>
        <div className="login-features">
          {['Theo dõi tồn kho theo thời gian thực, cảnh báo hàng sắp hết',
            'Nhập hàng, nhập doanh số POS cuối ngày, kiểm kê và đối soát',
            'Lưu trữ thông tin nhà cung cấp, hợp đồng và công nợ',
            'Báo cáo tồn kho, tiêu thụ, công nợ – xuất Excel / PDF'].map((t) => (
            <div className="login-feature" key={t}><CheckCircleOutlined />{t}</div>
          ))}
        </div>
      </div>
      <div className="login-form-wrap">
        <Card className="login-card" variant="borderless">
          <Typography.Title level={3} style={{ marginTop: 0 }}>Đăng nhập</Typography.Title>
          <Typography.Paragraph type="secondary">Nhập tên đăng nhập và mật khẩu được Quản lý cấp.</Typography.Paragraph>
          {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
          <Form form={form} layout="vertical" onFinish={submit} requiredMark={false} size="large">
            <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' }]}>
              <Input prefix={<UserOutlined />} placeholder="Ví dụ: nhanvien" autoComplete="username" autoFocus />
            </Form.Item>
            <Form.Item name="password" label="Mật khẩu" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="Mật khẩu" autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>Đăng nhập</Button>
          </Form>
        </Card>
      </div>
    </div>
  );
}
