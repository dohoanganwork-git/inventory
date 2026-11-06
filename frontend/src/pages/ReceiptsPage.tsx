import { Button, Card, DatePicker, Input, Select, Table } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { DebtStatusTag, DocStatusTag } from '../components/StatusTags';
import { useFetch } from '../utils/useFetch';
import type { Receipt, Supplier } from '../api/types';
import { fmtDate, fmtMoney, toIso } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function ReceiptsPage() {
  const { hasRole, canSeeValues } = useAuth();
  const nav = useNavigate();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [supplierId, setSupplierId] = useState<string>();
  const [status, setStatus] = useState<string>();
  const [debtStatus, setDebtStatus] = useState<string>();
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const { data: suppliers } = useFetch<Supplier[]>('/suppliers');
  const { data, loading } = useFetch<Receipt[]>('/receipts', {
    from: toIso(range?.[0]), to: toIso(range?.[1]), supplierId, status, debtStatus, q: query || undefined,
  });

  return (
    <>
      <PageHeader title="Phiếu nhập hàng" subtitle="Mỗi phiếu nhập làm tăng tồn kho và tự động tạo công nợ với nhà cung cấp (mặc định thanh toán sau)"
        extra={hasRole('STAFF', 'MANAGER') && <Button type="primary" icon={<PlusOutlined />} onClick={() => nav('/phieu-nhap/tao')}>Tạo phiếu nhập</Button>} />
      <Card className="section-card">
        <div className="filter-bar">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Mã phiếu, số hóa đơn, NCC... (Enter)" value={q}
            onChange={(e) => { setQ(e.target.value); if (!e.target.value) setQuery(''); }} onPressEnter={() => setQuery(q)} style={{ maxWidth: 280 }} />
          <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={(v) => setRange(v)} placeholder={['Từ ngày', 'Đến ngày']} />
          <Select allowClear showSearch optionFilterProp="label" placeholder="Nhà cung cấp" value={supplierId} onChange={setSupplierId} style={{ minWidth: 220 }}
            options={(suppliers ?? []).map((s) => ({ value: s.id, label: s.name }))} />
          <Select allowClear placeholder="Trạng thái phiếu" value={status} onChange={setStatus}
            options={[{ value: 'ACTIVE', label: 'Đã ghi nhận' }, { value: 'CANCELLED', label: 'Đã hủy' }]} />
          {canSeeValues && (
            <Select allowClear placeholder="Tình trạng công nợ" value={debtStatus} onChange={setDebtStatus}
              options={[{ value: 'UNPAID', label: 'Chưa thanh toán' }, { value: 'PARTIAL', label: 'Trả một phần' }, { value: 'PAID', label: 'Đã thanh toán' }]} />
          )}
        </div>
        <Table<Receipt> rowKey="id" loading={loading} dataSource={data ?? []} scroll={{ x: 1000 }}
          pagination={{ pageSize: 20, hideOnSinglePage: true, showTotal: (t) => `${t} phiếu` }}
          rowClassName={(r) => `row-clickable ${r.status === 'CANCELLED' ? 'row-muted' : ''}`}
          onRow={(r) => ({ onClick: () => nav(`/phieu-nhap/${r.id}`) })}
          locale={{ emptyText: 'Không có phiếu nhập phù hợp' }}
          columns={[
            { title: 'Mã phiếu', dataIndex: 'code', width: 165, render: (v) => <b>{v}</b> },
            { title: 'Ngày nhập', dataIndex: 'receiptDate', width: 110, render: fmtDate },
            { title: 'Nhà cung cấp', dataIndex: ['supplier', 'name'] },
            { title: 'Số hóa đơn', dataIndex: 'invoiceNo', width: 140 },
            { title: 'Mặt hàng / SL', width: 120, render: (_, r) => `${r.lineCount} MH · ${r.totalQuantity}` },
            { title: 'Tổng tiền', dataIndex: 'totalAmount', width: 140, align: 'right', render: (v) => <b>{fmtMoney(v)}</b> },
            ...(canSeeValues ? [
              { title: 'Còn nợ', dataIndex: 'remainingAmount', width: 130, align: 'right' as const, render: (v: number, r: Receipt) => (r.status === 'CANCELLED' ? '—' : fmtMoney(v)) },
              { title: 'Công nợ', width: 190, render: (_: unknown, r: Receipt) => (r.status === 'CANCELLED' ? <DocStatusTag status="CANCELLED" /> : <DebtStatusTag status={r.debtStatus} overdue={r.overdue} />) },
            ] : [{ title: 'Trạng thái', width: 130, render: (_: unknown, r: Receipt) => <DocStatusTag status={r.status} /> }]),
            { title: 'Người lập', dataIndex: ['createdBy', 'fullName'], width: 150 },
          ]} />
      </Card>
    </>
  );
}
