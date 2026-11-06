import { Button, Card, Col, Input, Progress, Row, Segmented, Select, Table, Tooltip } from 'antd';
import { SearchOutlined, FileExcelOutlined, FilePdfOutlined, DatabaseOutlined, WarningOutlined, ExclamationCircleOutlined, AppstoreOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import { StockStatusTag } from '../components/StatusTags';
import ItemDrawer from '../components/ItemDrawer';
import { useFetch } from '../utils/useFetch';
import { download } from '../api/client';
import type { Category, Item, Supplier } from '../api/types';
import { fmtMoney, fmtMoneyShort, fmtNumber, normalize } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

function StockBar({ item }: { item: Item }) {
  const cap = item.maxStock ?? Math.max(item.minStock * 4, item.currentStock, 1);
  const pct = Math.min(100, Math.round((item.currentStock / cap) * 100));
  const color = item.stockStatus === 'LOW' ? '#e34948' : item.stockStatus === 'OVER' ? '#6250d6' : '#1baf7a';
  return (
    <Tooltip title={`Tồn ${item.currentStock} · mốc cảnh báo ${item.minStock}${item.maxStock ? ` · tối đa ${item.maxStock}` : ''}`}>
      <div className="stock-bar"><Progress percent={pct} showInfo={false} strokeColor={color} size="small" /><b className="num">{item.currentStock}</b></div>
    </Tooltip>
  );
}

export default function InventoryPage() {
  const { canSeeValues } = useAuth();
  const [params] = useSearchParams();
  const { data, loading } = useFetch<Item[]>('/items', { active: true });
  const { data: categories } = useFetch<Category[]>('/categories');
  const { data: suppliers } = useFetch<Supplier[]>('/suppliers');
  const [q, setQ] = useState('');
  const [categoryId, setCategoryId] = useState<string>();
  const [supplierId, setSupplierId] = useState<string>();
  const [status, setStatus] = useState<string>(params.get('status') ?? 'all');
  const [viewing, setViewing] = useState<string | null>(null);

  const all = data ?? [];
  const rows = useMemo(() => all.filter((i) => (status === 'all' || i.stockStatus === status)
    && (!categoryId || i.category.id === categoryId) && (!supplierId || i.supplier?.id === supplierId)
    && (!q || normalize(`${i.code} ${i.posCode} ${i.name}`).includes(normalize(q)))), [all, q, categoryId, supplierId, status]);

  const totalQty = rows.reduce((s, i) => s + i.currentStock, 0);
  const totalValue = rows.reduce((s, i) => s + (i.stockValue ?? 0), 0);
  const low = all.filter((i) => i.stockStatus === 'LOW').length;
  const over = all.filter((i) => i.stockStatus === 'OVER').length;

  return (
    <>
      <PageHeader title="Tồn kho" subtitle="Số lượng tồn của từng mặt hàng theo thời gian thực (cập nhật sau mỗi phiếu nhập, doanh số POS và điều chỉnh kiểm kê)"
        extra={<>
          <Button icon={<FileExcelOutlined />} onClick={() => download('/reports/ton-kho/export', { format: 'xlsx' })}>Xuất Excel</Button>
          <Button icon={<FilePdfOutlined />} onClick={() => download('/reports/ton-kho/export', { format: 'pdf' })}>Xuất PDF</Button>
        </>} />
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}><StatCard title="Mặt hàng đang hiển thị" value={rows.length} icon={<AppstoreOutlined />} tone="info" hint={`${fmtNumber(totalQty)} đơn vị tồn`} /></Col>
        <Col xs={12} md={6}><StatCard title="Sắp hết / hết hàng" value={low} icon={<WarningOutlined />} tone={low ? 'danger' : 'success'} onClick={() => setStatus('LOW')} hint="Bấm để lọc" /></Col>
        <Col xs={12} md={6}><StatCard title="Tồn vượt mức" value={over} icon={<ExclamationCircleOutlined />} tone={over ? 'warning' : 'success'} onClick={() => setStatus('OVER')} hint="Bấm để lọc" /></Col>
        {canSeeValues && <Col xs={12} md={6}><StatCard title="Giá trị tồn (đang lọc)" value={fmtMoneyShort(totalValue)} icon={<DatabaseOutlined />} hint={fmtMoney(totalValue)} /></Col>}
      </Row>
      <Card className="section-card">
        <div className="filter-bar">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm mặt hàng, mã hàng, mã POS..." value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 300 }} />
          <Select allowClear placeholder="Loại hàng" value={categoryId} onChange={setCategoryId} options={(categories ?? []).map((c) => ({ value: c.id, label: c.name }))} />
          <Select allowClear showSearch optionFilterProp="label" placeholder="Nhà cung cấp" value={supplierId} onChange={setSupplierId} style={{ minWidth: 220 }}
            options={(suppliers ?? []).map((s) => ({ value: s.id, label: s.name }))} />
          <Segmented value={status} onChange={(v) => setStatus(v as string)}
            options={[{ label: 'Tất cả', value: 'all' }, { label: 'Tồn thấp', value: 'LOW' }, { label: 'Vượt mức', value: 'OVER' }, { label: 'Bình thường', value: 'NORMAL' }]} />
        </div>
        <Table<Item> rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 1000 }}
          pagination={{ pageSize: 25, hideOnSinglePage: true }}
          rowClassName={(r) => `row-clickable ${r.currentStock === 0 ? 'row-danger' : r.stockStatus === 'LOW' ? 'row-warn' : ''}`}
          onRow={(r) => ({ onClick: () => setViewing(r.id) })}
          locale={{ emptyText: 'Không tìm thấy mặt hàng phù hợp' }}
          summary={() => (
            <Table.Summary.Row>
              <Table.Summary.Cell index={0} colSpan={4}><b>Tổng cộng ({rows.length} mặt hàng)</b></Table.Summary.Cell>
              <Table.Summary.Cell index={4}><b>{fmtNumber(totalQty)}</b></Table.Summary.Cell>
              <Table.Summary.Cell index={5} colSpan={canSeeValues ? 2 : 2} />
              {canSeeValues && <Table.Summary.Cell index={7} align="right"><b>{fmtMoney(totalValue)}</b></Table.Summary.Cell>}
            </Table.Summary.Row>
          )}
          columns={[
            { title: 'Mã hàng', dataIndex: 'code', width: 120, render: (v) => <b>{v}</b> },
            { title: 'Mặt hàng', dataIndex: 'name', render: (v, r) => <>{v}<div className="muted small">{r.category.name} · {r.supplier?.name ?? '—'}</div></> },
            { title: 'ĐVT', dataIndex: 'unit', width: 70 },
            { title: 'Mốc / tối đa', width: 110, render: (_, r) => `${r.minStock} / ${r.maxStock ?? '—'}` },
            { title: 'Tồn kho', dataIndex: 'currentStock', width: 190, sorter: (a, b) => a.currentStock - b.currentStock, render: (_, r) => <StockBar item={r} /> },
            { title: 'Trạng thái', width: 140, render: (_, r) => <StockStatusTag status={r.stockStatus} stock={r.currentStock} />,
              sorter: (a, b) => a.stockStatus.localeCompare(b.stockStatus) },
            ...(canSeeValues ? [
              { title: 'Giá vốn', dataIndex: 'lastCost', width: 120, align: 'right' as const, render: fmtMoney },
              { title: 'Giá trị tồn', dataIndex: 'stockValue', width: 140, align: 'right' as const, render: fmtMoney,
                sorter: (a: Item, b: Item) => (a.stockValue ?? 0) - (b.stockValue ?? 0) },
            ] : [{ title: '', width: 1, render: () => null }]),
          ]} />
      </Card>
      <ItemDrawer itemId={viewing} onClose={() => setViewing(null)} />
    </>
  );
}
