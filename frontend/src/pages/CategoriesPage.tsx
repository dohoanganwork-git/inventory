import { Button, Card, Form, Input, Modal, Segmented, Space, Table, Tooltip } from 'antd';
import { EditOutlined, PlusOutlined, StopOutlined, CheckOutlined, SearchOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { ActiveTag } from '../components/StatusTags';
import { useFetch } from '../utils/useFetch';
import { api, showError } from '../api/client';
import type { Category } from '../api/types';
import { ui } from '../utils/globals';
import { normalize } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function CategoriesPage() {
  const { hasRole } = useAuth();
  const isManager = hasRole('MANAGER');
  const nav = useNavigate();
  const { data, loading, reload } = useFetch<Category[]>('/categories');
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<'all' | 'active' | 'inactive'>('active');
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      if (editing === 'new') await api.post('/categories', v);
      else if (editing) await api.put(`/categories/${editing.id}`, v);
      ui.message.success(editing === 'new' ? 'Thêm loại hàng thành công' : 'Cập nhật thành công');
      setEditing(null);
      reload();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  const toggle = (c: Category) => ui.modal.confirm({
    title: c.active ? `Ngừng sử dụng loại hàng "${c.name}"?` : `Sử dụng lại loại hàng "${c.name}"?`,
    content: c.active ? 'Loại hàng ngừng sử dụng sẽ không xuất hiện khi thêm mặt hàng mới. Dữ liệu cũ vẫn được giữ.' : undefined,
    okText: 'Xác nhận', cancelText: 'Đóng',
    onOk: async () => {
      try { await api.patch(`/categories/${c.id}/status`, { active: !c.active }); reload(); } catch (e) { showError(e); }
    },
  });

  const rows = (data ?? []).filter((c) => (status === 'all' || c.active === (status === 'active'))
    && (!q || normalize(`${c.code} ${c.name} ${c.description ?? ''}`).includes(normalize(q))));

  return (
    <>
      <PageHeader title="Loại hàng" subtitle="Phân nhóm sản phẩm: bia, rượu vang, đồ uống đóng chai/lon, merchandise..."
        extra={isManager && <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditing('new'); form.resetFields(); }}>Thêm loại hàng</Button>} />
      <Card className="section-card">
        <div className="filter-bar">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo mã, tên..." value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 300 }} />
          <Segmented value={status} onChange={(v) => setStatus(v as typeof status)}
            options={[{ label: 'Đang sử dụng', value: 'active' }, { label: 'Ngừng sử dụng', value: 'inactive' }, { label: 'Tất cả', value: 'all' }]} />
        </div>
        <Table<Category> rowKey="id" loading={loading} dataSource={rows} pagination={false} scroll={{ x: 640 }}
          locale={{ emptyText: 'Không tìm thấy dữ liệu' }}
          columns={[
            { title: 'Mã', dataIndex: 'code', width: 120, render: (v) => <b>{v}</b> },
            { title: 'Tên loại hàng', dataIndex: 'name' },
            { title: 'Mô tả', dataIndex: 'description', render: (v) => v ?? '—' },
            { title: 'Số mặt hàng', dataIndex: 'itemCount', align: 'right', width: 120,
              render: (v, r) => <Button type="link" size="small" onClick={() => nav(`/mat-hang?categoryId=${r.id}`)}>{v}</Button> },
            { title: 'Trạng thái', dataIndex: 'active', width: 150, render: (v) => <ActiveTag active={v} /> },
            ...(isManager ? [{
              title: 'Thao tác', width: 110, render: (_: unknown, r: Category) => (
                <Space>
                  <Tooltip title="Chỉnh sửa"><Button icon={<EditOutlined />} onClick={() => { setEditing(r); form.setFieldsValue(r); }} /></Tooltip>
                  <Tooltip title={r.active ? 'Ngừng sử dụng' : 'Sử dụng lại'}><Button icon={r.active ? <StopOutlined /> : <CheckOutlined />} onClick={() => toggle(r)} /></Tooltip>
                </Space>
              ),
            }] : []),
          ]} />
      </Card>
      <Modal open={!!editing} title={editing === 'new' ? 'Thêm loại hàng' : 'Chỉnh sửa loại hàng'} onOk={save} onCancel={() => setEditing(null)}
        okText="Lưu" cancelText="Hủy" confirmLoading={saving} destroyOnHidden>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="code" label="Mã loại hàng" rules={[{ required: true, message: 'Vui lòng nhập mã' }, { max: 30 }]}><Input placeholder="vd: BIA" style={{ textTransform: 'uppercase' }} /></Form.Item>
          <Form.Item name="name" label="Tên loại hàng" rules={[{ required: true, message: 'Vui lòng nhập tên' }]}><Input placeholder="vd: Bia thủ công" /></Form.Item>
          <Form.Item name="description" label="Mô tả"><Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
        </Form>
      </Modal>
    </>
  );
}
