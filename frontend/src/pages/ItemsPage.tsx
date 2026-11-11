import { Button, Card, Input, Select, Segmented, Space, Table, Tooltip } from 'antd';
import { EditOutlined, PlusOutlined, StopOutlined, CheckOutlined, SearchOutlined, EyeOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { ActiveTag, StockStatusTag } from '../components/StatusTags';
import ItemDrawer from '../components/ItemDrawer';
import ItemFormModal from '../components/ItemFormModal';
import { useFetch } from '../utils/useFetch';
import { api, parseError, showError } from '../api/client';
import type { Category, Item, Supplier } from '../api/types';
import { ui } from '../utils/globals';
import { fmtMoney, normalize } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function ItemsPage() {
  const { hasRole } = useAuth();
  const isManager = hasRole('MANAGER');
  const [params] = useSearchParams();
  const { data, loading, reload } = useFetch<Item[]>('/items');
  const { data: categories } = useFetch<Category[]>('/categories');
  const { data: suppliers } = useFetch<Supplier[]>('/suppliers');
  const [q, setQ] = useState('');
  const [categoryId, setCategoryId] = useState<string | undefined>(params.get('categoryId') ?? undefined);
  const [supplierId, setSupplierId] = useState<string | undefined>(params.get('supplierId') ?? undefined);
  const [status, setStatus] = useState<'active' | 'inactive' | 'all'>('active');
  const [viewing, setViewing] = useState<string | null>(null);
  const [editing, setEditing] = useState<Item | 'new' | null>(null);

  const rows = useMemo(() => (data ?? []).filter((i) =>
    (status === 'all' || i.active === (status === 'active'))
    && (!categoryId || i.category.id === categoryId)
    && (!supplierId || i.supplier?.id === supplierId)
    && (!q || normalize(`${i.code} ${i.posCode} ${i.name}`).includes(normalize(q)))), [data, q, categoryId, supplierId, status]);

  const setActive = async (i: Item, active: boolean, confirm = false) => {
    try {
      await api.patch(`/items/${i.id}/status`, { active, confirm });
      ui.message.success(active ? 'Đã sử dụng lại mặt hàng' : 'Đã ngừng sử dụng mặt hàng');
      reload();
    } catch (e) {
      const err = await parseError(e);
      if (err.code === 'NEED_CONFIRM') {
        ui.modal.confirm({ title: 'Mặt hàng vẫn còn tồn kho', content: err.message, okText: 'Vẫn ngừng sử dụng', okButtonProps: { danger: true },
          cancelText: 'Hủy thao tác', onOk: () => setActive(i, active, true) });
      } else showError(e);
    }
  };

  const toggle = (i: Item) => {
    if (!i.active) return setActive(i, true);
    ui.modal.confirm({
      title: `Ngừng sử dụng "${i.name}"?`, content: 'Mặt hàng sẽ không xuất hiện khi lập phiếu nhập mới. Lịch sử giao dịch vẫn được giữ.',
      okText: 'Ngừng sử dụng', cancelText: 'Đóng', onOk: () => setActive(i, false),
    });
  };

  return (
    <>
      <PageHeader title="Mặt hàng" subtitle="Danh mục sản phẩm nhập về để bán lại: mã hàng, mã POS, đơn vị tính, mốc cảnh báo tồn thấp"
        extra={isManager && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>Thêm mặt hàng</Button>} />
      <Card className="section-card">
        <div className="filter-bar">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên, mã hàng, mã POS..." value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 300 }} />
          <Select allowClear placeholder="Loại hàng" value={categoryId} onChange={setCategoryId}
            options={(categories ?? []).map((c) => ({ value: c.id, label: c.name }))} />
          <Select allowClear showSearch optionFilterProp="label" placeholder="Nhà cung cấp" value={supplierId} onChange={setSupplierId} style={{ minWidth: 220 }}
            options={(suppliers ?? []).map((s) => ({ value: s.id, label: s.name }))} />
          <Segmented value={status} onChange={(v) => setStatus(v as typeof status)}
            options={[{ label: 'Đang sử dụng', value: 'active' }, { label: 'Ngừng sử dụng', value: 'inactive' }, { label: 'Tất cả', value: 'all' }]} />
        </div>
        <Table<Item> rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 1355 }}
          pagination={{ pageSize: 20, hideOnSinglePage: true, showTotal: (t) => `${t} mặt hàng` }}
          locale={{ emptyText: 'Không tìm thấy mặt hàng phù hợp' }}
          rowClassName={(r) => `row-clickable ${r.active ? '' : 'row-muted'}`}
          onRow={(r) => ({ onClick: () => setViewing(r.id) })}
          columns={[
            { title: 'Mã hàng', dataIndex: 'code', width: 120, render: (v) => <b>{v}</b>, sorter: (a, b) => a.code.localeCompare(b.code) },
            { title: 'Mã POS', dataIndex: 'posCode', width: 110 },
            { title: 'Tên mặt hàng', dataIndex: 'name', width: 230, sorter: (a, b) => a.name.localeCompare(b.name, 'vi') },
            { title: 'Loại hàng', dataIndex: ['category', 'name'], width: 130 },
            { title: 'Nhà cung cấp', dataIndex: ['supplier', 'name'], width: 160, render: (v) => v ?? '—' },
            { title: 'ĐVT', dataIndex: 'unit', width: 65 },
            { title: 'Tồn', dataIndex: 'currentStock', width: 85, align: 'right', sorter: (a, b) => a.currentStock - b.currentStock },
            { title: 'Mốc', dataIndex: 'minStock', width: 65, align: 'right' },
            { title: 'Giá bán', dataIndex: 'salePrice', width: 110, align: 'right', render: fmtMoney },
            { title: 'Trạng thái', width: 140, render: (_, r) => (r.active ? <StockStatusTag status={r.stockStatus} stock={r.currentStock} /> : <ActiveTag active={false} />) },
            {
              title: 'Thao tác', fixed: 'right', width: isManager ? 140 : 70, render: (_, r) => (
                <Space onClick={(e) => e.stopPropagation()}>
                  <Tooltip title="Xem chi tiết & thẻ kho"><Button icon={<EyeOutlined />} onClick={() => setViewing(r.id)} /></Tooltip>
                  {isManager && <Tooltip title="Chỉnh sửa"><Button icon={<EditOutlined />} onClick={() => setEditing(r)} /></Tooltip>}
                  {isManager && <Tooltip title={r.active ? 'Ngừng sử dụng' : 'Sử dụng lại'}><Button icon={r.active ? <StopOutlined /> : <CheckOutlined />} onClick={() => toggle(r)} /></Tooltip>}
                </Space>
              ),
            },
          ]} />
      </Card>
      <ItemDrawer itemId={viewing} onClose={() => setViewing(null)} />
      <ItemFormModal open={!!editing} item={editing === 'new' ? null : editing} categories={categories ?? []} suppliers={suppliers ?? []}
        onClose={() => setEditing(null)} onSaved={() => { setEditing(null); reload(); }} />
    </>
  );
}
