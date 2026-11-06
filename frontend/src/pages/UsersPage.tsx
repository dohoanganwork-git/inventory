import { Button, Card, Form, Input, Modal, Select, Space, Table, Tooltip } from 'antd';
import { EditOutlined, PlusOutlined, StopOutlined, CheckOutlined, SearchOutlined } from '@ant-design/icons';
import { useState } from 'react';
import PageHeader from '../components/PageHeader';
import { ActiveTag, RoleTag } from '../components/StatusTags';
import { useFetch } from '../utils/useFetch';
import { api, showError } from '../api/client';
import type { User } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, normalize } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

const ROLE_OPTIONS = [
  { value: 'MANAGER', label: 'Quản lý' },
  { value: 'STAFF', label: 'Nhân viên' },
  { value: 'ACCOUNTANT', label: 'Kế toán' },
];

export default function UsersPage() {
  const { user: me } = useAuth();
  const { data, loading, reload } = useFetch<User[]>('/users');
  const [q, setQ] = useState('');
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const open = (u: User | 'new') => {
    setEditing(u);
    form.resetFields();
    if (u !== 'new') form.setFieldsValue(u);
    else form.setFieldsValue({ role: 'STAFF' });
  };

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      if (editing === 'new') {
        await api.post('/users', v);
        ui.message.success('Tạo tài khoản thành công');
      } else if (editing) {
        await api.put(`/users/${editing.id}`, v);
        ui.message.success('Cập nhật tài khoản thành công');
      }
      setEditing(null);
      reload();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  const toggle = (u: User) => {
    ui.modal.confirm({
      title: u.active ? `Vô hiệu hóa tài khoản "${u.username}"?` : `Kích hoạt lại tài khoản "${u.username}"?`,
      content: u.active ? 'Người dùng sẽ không thể đăng nhập. Lịch sử thao tác vẫn được giữ lại.' : 'Người dùng có thể đăng nhập trở lại.',
      okText: u.active ? 'Vô hiệu hóa' : 'Kích hoạt', okButtonProps: { danger: u.active }, cancelText: 'Đóng',
      onOk: async () => {
        try {
          await api.patch(`/users/${u.id}/status`, { active: !u.active });
          ui.message.success('Đã cập nhật trạng thái tài khoản');
          reload();
        } catch (e) { showError(e); }
      },
    });
  };

  const rows = (data ?? []).filter((u) => !q || normalize(`${u.username} ${u.fullName} ${u.phone ?? ''} ${u.email ?? ''}`).includes(normalize(q)));

  return (
    <>
      <PageHeader title="Tài khoản người dùng" subtitle="Tạo, chỉnh sửa, phân quyền và vô hiệu hóa tài khoản (hệ thống không xóa tài khoản để giữ lịch sử thao tác)"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>Tạo tài khoản</Button>} />
      <Card className="section-card">
        <div className="filter-bar">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên, tên đăng nhập, SĐT..." value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 340 }} />
        </div>
        <Table<User> rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 820 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
          rowClassName={(r) => (r.active ? '' : 'row-muted')}
          columns={[
            { title: 'Họ và tên', dataIndex: 'fullName', render: (v, r) => <><b>{v}</b>{r.id === me?.id && <span className="muted"> (bạn)</span>}</> },
            { title: 'Tên đăng nhập', dataIndex: 'username' },
            { title: 'Vai trò', dataIndex: 'role', render: (v) => <RoleTag role={v} />,
              filters: ROLE_OPTIONS.map((o) => ({ text: o.label, value: o.value })), onFilter: (v, r) => r.role === v },
            { title: 'Số điện thoại', dataIndex: 'phone', render: (v) => v ?? '—' },
            { title: 'Email', dataIndex: 'email', render: (v) => v ?? '—' },
            { title: 'Ngày tạo', dataIndex: 'createdAt', render: fmtDate },
            { title: 'Trạng thái', dataIndex: 'active', render: (v) => <ActiveTag active={v} on="Hoạt động" off="Đã vô hiệu hóa" /> },
            {
              title: 'Thao tác', fixed: 'right', width: 110, render: (_, r) => (
                <Space>
                  <Tooltip title="Chỉnh sửa"><Button icon={<EditOutlined />} onClick={() => open(r)} /></Tooltip>
                  <Tooltip title={r.active ? 'Vô hiệu hóa' : 'Kích hoạt lại'}>
                    <Button danger={r.active} icon={r.active ? <StopOutlined /> : <CheckOutlined />} onClick={() => toggle(r)} disabled={r.id === me?.id} />
                  </Tooltip>
                </Space>
              ),
            },
          ]} />
      </Card>

      <Modal open={!!editing} title={editing === 'new' ? 'Tạo tài khoản mới' : 'Chỉnh sửa tài khoản'} onCancel={() => setEditing(null)}
        onOk={save} okText="Lưu" cancelText="Hủy" confirmLoading={saving} destroyOnHidden>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="username" label="Tên đăng nhập" rules={[{ required: true, message: 'Vui lòng nhập tên đăng nhập' },
            { pattern: /^[a-zA-Z0-9._-]{3,50}$/, message: '3–50 ký tự: chữ không dấu, số, dấu chấm, gạch' }]}>
            <Input disabled={editing !== 'new'} placeholder="vd: nhanvien3" />
          </Form.Item>
          <Form.Item name="fullName" label="Họ và tên" rules={[{ required: true, message: 'Vui lòng nhập họ tên' }]}><Input /></Form.Item>
          <Form.Item name="role" label="Vai trò" rules={[{ required: true, message: 'Vui lòng chọn vai trò' }]}><Select options={ROLE_OPTIONS} /></Form.Item>
          <Space.Compact block style={{ gap: 12, display: 'flex' }}>
            <Form.Item name="phone" label="Số điện thoại" style={{ flex: 1 }} rules={[{ pattern: /^(\+84|0)[0-9]{9,10}$/, message: 'Không đúng định dạng' }]}><Input /></Form.Item>
            <Form.Item name="email" label="Email" style={{ flex: 1 }} rules={[{ type: 'email', message: 'Không đúng định dạng' }]}><Input /></Form.Item>
          </Space.Compact>
          {editing === 'new' ? (
            <Form.Item name="password" label="Mật khẩu ban đầu" rules={[{ required: true, message: 'Vui lòng nhập mật khẩu' }, { min: 8, message: 'Tối thiểu 8 ký tự' }]}>
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          ) : (
            <Form.Item name="newPassword" label="Đặt lại mật khẩu (để trống nếu không đổi)" rules={[{ min: 8, message: 'Tối thiểu 8 ký tự' }]}>
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </>
  );
}
