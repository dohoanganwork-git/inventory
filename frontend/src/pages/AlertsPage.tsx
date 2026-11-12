import { Button, Card, Col, Form, Input, InputNumber, Modal, Row, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { ImportOutlined, PhoneOutlined, SettingOutlined, WarningOutlined, SearchOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import { StockStatusTag } from '../components/StatusTags';
import { useFetch } from '../utils/useFetch';
import { api, showError } from '../api/client';
import type { Alerts, Item, Supplier } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, normalize } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function AlertsPage() {
  const { hasRole } = useAuth();
  const isManager = hasRole('MANAGER');
  const nav = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? 'low';
  const { data, loading, reload } = useFetch<Alerts>('/alerts');
  const { data: suppliers } = useFetch<Supplier[]>('/suppliers');
  const { data: items, reload: reloadItems } = useFetch<Item[]>(isManager ? '/items' : null, { active: true });
  const [editing, setEditing] = useState<Item | null>(null);
  const [q, setQ] = useState('');
  const [form] = Form.useForm();
  const phone = (id?: string) => suppliers?.find((s) => s.id === id)?.phone;

  const saveThreshold = async () => {
    const v = await form.validateFields().catch(() => null);
    if (!v) return;
    try {
      await api.patch(`/items/${editing!.id}/threshold`, v);
      ui.message.success('Đã cập nhật mốc cảnh báo');
      setEditing(null);
      reload();
      reloadItems();
    } catch (e) { showError(e); }
  };

  const suggested = (i: Item) => Math.max(0, (i.maxStock ?? i.minStock * 3) - i.currentStock);
  const restock = (i: Item) => nav(`/phieu-nhap/tao?supplierId=${i.supplier?.id ?? ''}&itemId=${i.id}`);

  const lowColumns = [
    { title: 'Mặt hàng', dataIndex: 'name', render: (v: string, r: Item) => <><b>{v}</b><div className="muted small">{r.code} · {r.category.name}</div></> },
    { title: 'Tồn hiện tại', dataIndex: 'currentStock', width: 110, align: 'right' as const, render: (v: number, r: Item) => <b style={{ color: v === 0 ? '#c4320a' : undefined }}>{v} {r.unit}</b> },
    { title: 'Mốc cảnh báo', dataIndex: 'minStock', width: 110, align: 'right' as const },
    { title: 'Gợi ý nhập', width: 110, align: 'right' as const, render: (_: unknown, r: Item) => <Tooltip title="Đưa tồn về mức tối đa (hoặc 3 lần mốc cảnh báo)">{suggested(r)}</Tooltip> },
    { title: 'Nhà cung cấp', width: 230, render: (_: unknown, r: Item) => <>{r.supplier?.name ?? '—'}{phone(r.supplier?.id) && <div className="small"><a href={`tel:${phone(r.supplier?.id)}`}><PhoneOutlined /> {phone(r.supplier?.id)}</a></div>}</> },
    { title: 'Trạng thái', width: 120, render: (_: unknown, r: Item) => <StockStatusTag status={r.stockStatus} stock={r.currentStock} /> },
    {
      title: 'Thao tác', width: isManager ? 200 : 120, render: (_: unknown, r: Item) => (
        <Space>
          <Button size="small" type="primary" icon={<ImportOutlined />} onClick={() => restock(r)}>Nhập thêm</Button>
          {isManager && <Button size="small" icon={<SettingOutlined />} onClick={() => { setEditing(r); form.setFieldsValue({ minStock: r.minStock, maxStock: r.maxStock }); }}>Mốc</Button>}
        </Space>
      ),
    },
  ];

  const tabs = [
    {
      key: 'low', label: <span><WarningOutlined /> Tồn thấp ({data?.lowStock.length ?? 0})</span>, children: (
        <Table<Item> rowKey="id" loading={loading} dataSource={data?.lowStock ?? []} columns={lowColumns} scroll={{ x: 1000 }} pagination={false}
          rowClassName={(r) => (r.currentStock === 0 ? 'row-danger' : 'row-warn')}
          locale={{ emptyText: 'Không có mặt hàng nào ở mức cảnh báo' }} />
      ),
    },
    {
      key: 'over', label: `Tồn vượt mức (${data?.overStock.length ?? 0})`, children: (
        <Table<Item> rowKey="id" loading={loading} dataSource={data?.overStock ?? []} scroll={{ x: 760 }} pagination={false}
          locale={{ emptyText: 'Không có mặt hàng tồn vượt mức quy định' }}
          columns={[
            { title: 'Mặt hàng', dataIndex: 'name', render: (v, r) => <><b>{v}</b><div className="muted small">{r.code}</div></> },
            { title: 'Tồn hiện tại', dataIndex: 'currentStock', align: 'right', width: 120 },
            { title: 'Mức tối đa', dataIndex: 'maxStock', align: 'right', width: 120 },
            { title: 'Vượt', width: 100, align: 'right', render: (_, r) => <Tag color="purple">+{r.currentStock - (r.maxStock ?? 0)}</Tag> },
            { title: 'Gợi ý', render: () => <span className="muted">Ưu tiên tư vấn khách chọn sản phẩm này, tạm dừng đặt thêm</span> },
            ...(isManager ? [{ title: '', width: 90, render: (_: unknown, r: Item) => <Button size="small" icon={<SettingOutlined />} onClick={() => { setEditing(r); form.setFieldsValue({ minStock: r.minStock, maxStock: r.maxStock }); }}>Mốc</Button> }] : []),
          ]} />
      ),
    },
    {
      key: 'oversold', label: `Bán vượt tồn 30 ngày (${data?.overSold.length ?? 0})`, children: (
        <Table rowKey={(r) => r.salesId + r.item.id} loading={loading} dataSource={data?.overSold ?? []} scroll={{ x: 760 }} pagination={false}
          locale={{ emptyText: 'Không có trường hợp bán vượt tồn' }}
          columns={[
            { title: 'Ngày bán', dataIndex: 'salesDate', width: 110, render: fmtDate },
            { title: 'Phiếu xuất', dataIndex: 'salesCode', width: 140, render: (v, r) => <a onClick={() => nav(`/phieu-xuat/${r.salesId}`)}>{v}</a> },
            { title: 'Mặt hàng', dataIndex: ['item', 'name'] },
            { title: 'SL bán (POS)', dataIndex: 'quantity', align: 'right', width: 120 },
            { title: 'Tồn trước khi trừ', dataIndex: 'stockBefore', align: 'right', width: 140 },
            { title: 'Vượt tồn', dataIndex: 'overQuantity', width: 110, render: (v) => <Tag color="error">{v}</Tag> },
          ]} />
      ),
    },
  ];
  if (isManager) {
    tabs.push({
      key: 'settings', label: <span><SettingOutlined /> Thiết lập mốc cảnh báo</span>, children: (
        <>
          <div className="filter-bar"><Input allowClear prefix={<SearchOutlined />} placeholder="Tìm mặt hàng..." value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 300 }} /></div>
          <Table<Item> rowKey="id" dataSource={(items ?? []).filter((i) => !q || normalize(`${i.code} ${i.name}`).includes(normalize(q)))} scroll={{ x: 700 }}
            pagination={{ pageSize: 20, hideOnSinglePage: true }}
            columns={[
              { title: 'Mặt hàng', dataIndex: 'name', render: (v, r) => <><b>{v}</b><div className="muted small">{r.code}</div></> },
              { title: 'Tồn', dataIndex: 'currentStock', align: 'right', width: 90 },
              { title: 'Mốc tồn thấp', dataIndex: 'minStock', align: 'right', width: 120 },
              { title: 'Mức tối đa', dataIndex: 'maxStock', align: 'right', width: 120, render: (v) => v ?? '—' },
              { title: 'Trạng thái', width: 130, render: (_, r) => <StockStatusTag status={r.stockStatus} stock={r.currentStock} /> },
              { title: '', width: 110, render: (_, r) => <Button size="small" icon={<SettingOutlined />} onClick={() => { setEditing(r); form.setFieldsValue({ minStock: r.minStock, maxStock: r.maxStock }); }}>Sửa mốc</Button> },
            ]} />
        </>
      ),
    } as (typeof tabs)[number]);
  }

  return (
    <>
      <PageHeader title="Cảnh báo tồn kho" subtitle="Mặt hàng tồn đến/dưới mốc cảnh báo, tồn vượt mức quy định và các trường hợp bán vượt tồn. Nhân viên dùng để đặt thêm hàng; Quản lý dùng để kiểm tra tồn kho." />
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}><StatCard title="Sắp hết" value={(data?.lowStock.length ?? 0) - (data?.outOfStock ?? 0)} tone="warning" onClick={() => setParams({ tab: 'low' })} /></Col>
        <Col xs={12} md={6}><StatCard title="Đã hết hàng" value={data?.outOfStock ?? 0} tone={data?.outOfStock ? 'danger' : 'success'} onClick={() => setParams({ tab: 'low' })} /></Col>
        <Col xs={12} md={6}><StatCard title="Tồn vượt mức" value={data?.overStock.length ?? 0} tone="info" onClick={() => setParams({ tab: 'over' })} /></Col>
        <Col xs={12} md={6}><StatCard title="Bán vượt tồn (30 ngày)" value={data?.overSold.length ?? 0} tone={data?.overSold.length ? 'danger' : 'success'} onClick={() => setParams({ tab: 'oversold' })} /></Col>
      </Row>
      <Card className="section-card">
        <Tabs activeKey={tab} onChange={(k) => setParams({ tab: k })} items={tabs} />
      </Card>
      <Modal open={!!editing} title={`Mốc cảnh báo – ${editing?.name ?? ''}`} onOk={saveThreshold} onCancel={() => setEditing(null)} okText="Lưu" cancelText="Hủy" destroyOnHidden>
        <Form form={form} layout="vertical">
          <Row gutter={12}>
            <Col span={12}><Form.Item name="minStock" label="Mốc tồn thấp" rules={[{ required: true, message: 'Nhập mốc' }, { type: 'number', min: 0, message: 'Không được nhỏ hơn 0' }]}
              extra="Cảnh báo khi tồn ≤ mốc này"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item></Col>
            <Col span={12}><Form.Item name="maxStock" label="Mức tồn tối đa" extra="Để trống nếu không giới hạn"><InputNumber min={0} style={{ width: '100%' }} /></Form.Item></Col>
          </Row>
        </Form>
      </Modal>
    </>
  );
}
