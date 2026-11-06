import { Button, Card, DatePicker, Select, Table, Tag } from 'antd';
import { ExportOutlined, WarningOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { DocStatusTag } from '../components/StatusTags';
import FileLink from '../components/FileLink';
import { useFetch } from '../utils/useFetch';
import type { Item, Sales } from '../api/types';
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber, toIso } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function SalesPage() {
  const { hasRole, canSeeValues } = useAuth();
  const nav = useNavigate();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [status, setStatus] = useState<string>();
  const [itemId, setItemId] = useState<string>();
  const { data: items } = useFetch<Item[]>('/items');
  const { data, loading } = useFetch<Sales[]>('/sales', { from: toIso(range?.[0]), to: toIso(range?.[1]), status, itemId });

  return (
    <>
      <PageHeader title="Doanh số POS / Phiếu xuất bán"
        subtitle="Cuối ngày Quản lý tải file doanh số xuất từ Sapo POS; hệ thống tạo phiếu xuất bán và trừ tồn kho (không kết nối trực tiếp POS)"
        extra={hasRole('MANAGER') && <Button type="primary" icon={<ExportOutlined />} onClick={() => nav('/phieu-xuat/nhap-pos')}>Nhập doanh số POS</Button>} />
      <Card className="section-card">
        <div className="filter-bar">
          <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={(v) => setRange(v)} placeholder={['Từ ngày', 'Đến ngày']} />
          <Select allowClear showSearch optionFilterProp="label" placeholder="Có chứa mặt hàng" value={itemId} onChange={setItemId} style={{ minWidth: 260 }}
            options={(items ?? []).map((i) => ({ value: i.id, label: `${i.code} – ${i.name}` }))} />
          <Select allowClear placeholder="Trạng thái" value={status} onChange={setStatus}
            options={[{ value: 'ACTIVE', label: 'Đã ghi nhận' }, { value: 'CANCELLED', label: 'Đã hủy' }]} />
        </div>
        <Table<Sales> rowKey="id" loading={loading} dataSource={data ?? []} scroll={{ x: 980 }}
          pagination={{ pageSize: 20, hideOnSinglePage: true, showTotal: (t) => `${t} phiếu` }}
          rowClassName={(r) => `row-clickable ${r.status === 'CANCELLED' ? 'row-muted' : ''}`}
          onRow={(r) => ({ onClick: () => nav(`/phieu-xuat/${r.id}`) })}
          locale={{ emptyText: 'Chưa có phiếu xuất bán' }}
          columns={[
            { title: 'Mã phiếu', dataIndex: 'code', width: 165, render: (v) => <b>{v}</b> },
            { title: 'Ngày bán', dataIndex: 'salesDate', width: 110, render: fmtDate },
            { title: 'Số mặt hàng', dataIndex: 'lineCount', width: 110, align: 'right' },
            { title: 'Tổng SL bán', dataIndex: 'totalQuantity', width: 120, align: 'right', render: fmtNumber },
            ...(canSeeValues ? [{ title: 'Doanh thu', dataIndex: 'totalAmount', width: 140, align: 'right' as const, render: fmtMoney }] : []),
            { title: 'Bán vượt tồn', dataIndex: 'overQuantity', width: 130, render: (v: number) => (v > 0 ? <Tag color="error" icon={<WarningOutlined />}>Vượt {v}</Tag> : <span className="muted">—</span>) },
            { title: 'File gốc', dataIndex: 'originalFile', render: (f) => <span onClick={(e) => e.stopPropagation()}><FileLink file={f} /></span> },
            { title: 'Người nhập', width: 170, render: (_, r) => <>{r.importedBy?.fullName}<div className="muted small">{fmtDateTime(r.importedAt)}</div></> },
            { title: 'Trạng thái', dataIndex: 'status', width: 120, render: (v) => <DocStatusTag status={v} /> },
          ]} />
      </Card>
    </>
  );
}
