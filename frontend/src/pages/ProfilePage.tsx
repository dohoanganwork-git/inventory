import { Button, Card, Col, Descriptions, Form, Input, Row, Tabs } from 'antd';
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { RoleTag } from '../components/StatusTags';
import { useAuth } from '../auth/AuthContext';
import { api, showError } from '../api/client';
import type { User } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate } from '../utils/format';

export default function ProfilePage() {
  const { user, setUser } = useAuth();
  const [params, setParams] = useSearchParams();
  const [saving, setSaving] = useState(false);
  const [profileForm] = Form.useForm();
  const [pwForm] = Form.useForm();
  if (!user) return null;

  const saveProfile = async (v: { fullName: string; email?: string; phone?: string }) => {
    setSaving(true);
    try {
      const { data } = await api.put<User>('/auth/me', v);
      setUser(data);
      ui.message.success('Cập nhật thông tin thành công');
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  const changePw = async (v: { currentPassword: string; newPassword: string; confirmPassword: string }) => {
    setSaving(true);
    try {
      await api.post('/auth/change-password', v);
      ui.message.success('Đổi mật khẩu thành công. Lần đăng nhập sau hãy dùng mật khẩu mới.');
      pwForm.resetFields();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  return (
    <>
      <PageHeader title="Tài khoản của tôi" subtitle="Xem và cập nhật thông tin cá nhân, đổi mật khẩu" />
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card className="section-card" title="Thông tin tài khoản">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Tên đăng nhập">{user.username}</Descriptions.Item>
              <Descriptions.Item label="Vai trò"><RoleTag role={user.role} /></Descriptions.Item>
              <Descriptions.Item label="Ngày tạo">{fmtDate(user.createdAt)}</Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
        <Col xs={24} lg={16}>
          <Card className="section-card">
            <Tabs activeKey={params.get('tab') === 'mat-khau' ? 'pw' : 'info'}
              onChange={(k) => setParams(k === 'pw' ? { tab: 'mat-khau' } : {})}
              items={[
                {
                  key: 'info', label: 'Thông tin cá nhân',
                  children: (
                    <Form form={profileForm} layout="vertical" initialValues={user} onFinish={saveProfile} style={{ maxWidth: 520 }}>
                      <Form.Item name="fullName" label="Họ và tên" rules={[{ required: true, message: 'Vui lòng nhập họ tên' }]}><Input /></Form.Item>
                      <Form.Item name="phone" label="Số điện thoại" rules={[{ pattern: /^(\+84|0)[0-9]{9,10}$/, message: 'Số điện thoại không đúng định dạng' }]}><Input /></Form.Item>
                      <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Email không đúng định dạng' }]}><Input /></Form.Item>
                      <Button onClick={() => profileForm.resetFields()} style={{ marginRight: 8 }}>Hủy thay đổi</Button>
                      <Button type="primary" htmlType="submit" loading={saving}>Lưu thông tin</Button>
                    </Form>
                  ),
                },
                {
                  key: 'pw', label: 'Đổi mật khẩu',
                  children: (
                    <Form form={pwForm} layout="vertical" onFinish={changePw} style={{ maxWidth: 420 }}>
                      <Form.Item name="currentPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu hiện tại' }]}>
                        <Input.Password autoComplete="current-password" />
                      </Form.Item>
                      <Form.Item name="newPassword" label="Mật khẩu mới" extra="Tối thiểu 8 ký tự, khác mật khẩu cũ"
                        rules={[{ required: true, message: 'Vui lòng nhập mật khẩu mới' }, { min: 8, message: 'Mật khẩu tối thiểu 8 ký tự' }]}>
                        <Input.Password autoComplete="new-password" />
                      </Form.Item>
                      <Form.Item name="confirmPassword" label="Xác nhận mật khẩu mới" dependencies={['newPassword']}
                        rules={[{ required: true, message: 'Vui lòng xác nhận mật khẩu' },
                          ({ getFieldValue }) => ({ validator: (_, v) => (!v || v === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('Xác nhận mật khẩu không khớp'))) })]}>
                        <Input.Password autoComplete="new-password" />
                      </Form.Item>
                      <Button type="primary" htmlType="submit" loading={saving}>Đổi mật khẩu</Button>
                    </Form>
                  ),
                },
              ]} />
          </Card>
        </Col>
      </Row>
    </>
  );
}
