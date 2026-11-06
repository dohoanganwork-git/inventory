import { Alert, Button, Card, Col, DatePicker, Row, Select, Space, Table, Tabs, Tag } from 'antd';
import { DollarOutlined, PhoneOutlined, WarningOutlined, FileExcelOutlined, FilePdfOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import FileLink from '../components/FileLink';
import { DebtStatusTag } from '../components/StatusTags';
import { PaymentModal } from '../components/PaymentModal';
import { useFetch } from '../utils/useFetch';
import { download } from '../api/client';
import type { Payment, Receipt, SupplierDebt } from '../api/types';
import { fmtDate, fmtMoney, fmtMoneyShort, toIso } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function DebtsPage() {
  const { hasRole } = useAuth();
  const isAccountant = hasRole('ACCOUNTANT');
  const nav = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') ?? 'ncc';
  const { data: summary, loading: l1, reload: r1 } = useFetch<SupplierDebt[]>('/debts/summary');
  const [supplierId, setSupplierId] = useState<string>();
  const [debtStatus, setDebtStatus] = useState<string>();
  const { data: receipts, loading: l2, reload: r2 } = useFetch<Receipt[]>('/receipts', { status: 'ACTIVE', supplierId, debtStatus, openDebt: debtStatus ? undefined : true });
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const { data: payments, loading: l3, reload: r3 } = useFetch<Payment[]>('/payments', { from: toIso(range?.[0]), to: toIso(range?.[1]) });
  const [selected, setSelected] = useState<string[]>([]);
  const [paying, setPaying] = useState<Receipt[] | null>(null);

  const totals = useMemo(() => (summary ?? []).reduce((a, s) => ({
    remain: a.remain + s.remainingAmount, overdue: a.overdue + s.overdueAmount, open: a.open + s.openReceiptCount,
  }), { remain: 0, overdue: 0, open: 0 }), [summary]);

  const selectedReceipts = (receipts ?? []).filter((r) => selected.includes(r.id));
  const sameSupplier = new Set(selectedReceipts.map((r) => r.supplier.id)).size <= 1;
  const reloadAll = () => { r1(); r2(); r3(); setSelected([]); };
  const payBySupplier = (supId: string) => { setSupplierId(supId); setDebtStatus(undefined); setParams({ tab: 'phieu' }); };

  return (
    <>
      <PageHeader title="Công nợ nhà cung cấp" subtitle="Theo dõi đơn nhập hàng, số đã thanh toán và còn nợ với từng nhà cung cấp"
        extra={<>
          <Button icon={<FileExcelOutlined />} onClick={() => download('/reports/cong-no/export', { format: 'xlsx' })}>Xuất Excel</Button>
          <Button icon={<FilePdfOutlined />} onClick={() => download('/reports/cong-no/export', { format: 'pdf' })}>Xuất PDF</Button>
        </>} />
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={8}><StatCard title="Tổng công nợ phải trả" value={fmtMoneyShort(totals.remain)} hint={fmtMoney(totals.remain)} icon={<DollarOutlined />} tone="info" /></Col>
        <Col xs={12} md={8}><StatCard title="Công nợ quá hạn" value={fmtMoneyShort(totals.overdue)} hint={fmtMoney(totals.overdue)} icon={<WarningOutlined />} tone={totals.overdue ? 'danger' : 'success'} /></Col>
        <Col xs={12} md={8}><StatCard title="Phiếu nhập còn nợ" value={totals.open} hint="phiếu chưa thanh toán hết" tone={totals.open ? 'warning' : 'success'}
          onClick={() => setParams({ tab: 'phieu' })} /></Col>
      </Row>
      <Card className="section-card">
        <Tabs activeKey={tab} onChange={(k) => setParams({ tab: k })} items={[
          {
            key: 'ncc', label: 'Theo nhà cung cấp', children: (
              <Table<SupplierDebt> rowKey={(r) => r.supplier.id} loading={l1} dataSource={summary ?? []} scroll={{ x: 1000 }} pagination={false}
                columns={[
                  { title: 'Nhà cung cấp', render: (_, r) => <><b>{r.supplier.name}</b><div className="muted small">{r.contactName}{r.phone && <> · <a href={`tel:${r.phone}`}><PhoneOutlined /> {r.phone}</a></>}</div></> },
                  { title: 'Số phiếu', width: 110, align: 'right', render: (_, r) => `${r.openReceiptCount} / ${r.receiptCount}` },
                  { title: 'Tổng nhập', dataIndex: 'totalAmount', align: 'right', width: 140, render: fmtMoney },
                  { title: 'Đã thanh toán', dataIndex: 'paidAmount', align: 'right', width: 140, render: fmtMoney },
                  { title: 'Còn nợ', dataIndex: 'remainingAmount', align: 'right', width: 140, render: (v) => <b>{fmtMoney(v)}</b>, sorter: (a, b) => a.remainingAmount - b.remainingAmount, defaultSortOrder: 'descend' },
                  { title: 'Quá hạn', dataIndex: 'overdueAmount', align: 'right', width: 130, render: (v: number) => (v > 0 ? <Tag color="error">{fmtMoney(v)}</Tag> : '—') },
                  { title: 'Nợ cũ nhất từ', dataIndex: 'oldestOpenDate', width: 120, render: fmtDate },
                  { title: '', width: 150, render: (_, r) => r.remainingAmount > 0 && (
                    <Button size="small" type={isAccountant ? 'primary' : 'default'} onClick={() => payBySupplier(r.supplier.id)}>{isAccountant ? 'Thanh toán' : 'Xem phiếu nợ'}</Button>) },
                ]}
                summary={() => (
                  <Table.Summary.Row>
                    <Table.Summary.Cell index={0} colSpan={4}><b>Tổng cộng</b></Table.Summary.Cell>
                    <Table.Summary.Cell index={4} align="right"><b>{fmtMoney(totals.remain)}</b></Table.Summary.Cell>
                    <Table.Summary.Cell index={5} align="right"><b>{fmtMoney(totals.overdue)}</b></Table.Summary.Cell>
                    <Table.Summary.Cell index={6} colSpan={2} />
                  </Table.Summary.Row>
                )} />
            ),
          },
          {
            key: 'phieu', label: 'Danh sách phiếu nợ', children: (
              <>
                <div className="filter-bar">
                  <Select allowClear showSearch optionFilterProp="label" placeholder="Nhà cung cấp" value={supplierId} onChange={(v) => { setSupplierId(v); setSelected([]); }} style={{ minWidth: 240 }}
                    options={(summary ?? []).map((s) => ({ value: s.supplier.id, label: s.supplier.name }))} />
                  <Select allowClear placeholder="Còn nợ (mặc định)" value={debtStatus} onChange={setDebtStatus}
                    options={[{ value: 'UNPAID', label: 'Chưa thanh toán' }, { value: 'PARTIAL', label: 'Trả một phần' }, { value: 'PAID', label: 'Đã thanh toán' }]} />
                  {isAccountant && (
                    <Button type="primary" icon={<DollarOutlined />} disabled={!selected.length || !sameSupplier} onClick={() => setPaying(selectedReceipts)}>
                      Thanh toán {selected.length ? `${selected.length} phiếu đã chọn` : 'gộp'}
                    </Button>
                  )}
                </div>
                {isAccountant && !sameSupplier && <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="Chỉ thanh toán gộp được các phiếu của cùng một nhà cung cấp" />}
                <Table<Receipt> rowKey="id" loading={l2} dataSource={receipts ?? []} scroll={{ x: 1000 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
                  rowSelection={isAccountant ? { selectedRowKeys: selected, onChange: (k) => setSelected(k as string[]),
                    getCheckboxProps: (r) => ({ disabled: (r.remainingAmount ?? 0) <= 0 }) } : undefined}
                  columns={[
                    { title: 'Mã phiếu', dataIndex: 'code', width: 165, render: (v, r) => <a onClick={() => nav(`/phieu-nhap/${r.id}`)}><b>{v}</b></a> },
                    { title: 'Ngày nhập', dataIndex: 'receiptDate', width: 105, render: fmtDate },
                    { title: 'Nhà cung cấp', dataIndex: ['supplier', 'name'] },
                    { title: 'Số HĐ', dataIndex: 'invoiceNo', width: 130 },
                    { title: 'Tổng tiền', dataIndex: 'totalAmount', align: 'right', width: 130, render: fmtMoney },
                    { title: 'Đã trả', dataIndex: 'paidAmount', align: 'right', width: 120, render: fmtMoney },
                    { title: 'Còn nợ', dataIndex: 'remainingAmount', align: 'right', width: 130, render: (v) => <b>{fmtMoney(v)}</b> },
                    { title: 'Hạn TT', dataIndex: 'dueDate', width: 105, render: fmtDate },
                    { title: 'Tình trạng', width: 200, render: (_, r) => <DebtStatusTag status={r.debtStatus} overdue={r.overdue} /> },
                    ...(isAccountant ? [{ title: '', width: 110, render: (_: unknown, r: Receipt) => (r.remainingAmount ?? 0) > 0 && (
                      <Button size="small" onClick={() => setPaying([r])}>Thanh toán</Button>) }] : []),
                  ]} />
              </>
            ),
          },
          {
            key: 'tt', label: 'Lịch sử thanh toán', children: (
              <>
                <div className="filter-bar">
                  <DatePicker.RangePicker format="DD/MM/YYYY" value={range} onChange={(v) => setRange(v)} placeholder={['Từ ngày', 'Đến ngày']} />
                </div>
                <Table<Payment> rowKey="id" loading={l3} dataSource={payments ?? []} scroll={{ x: 900 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
                  columns={[
                    { title: 'Mã', dataIndex: 'code', width: 130 },
                    { title: 'Ngày TT', dataIndex: 'paidAt', width: 105, render: fmtDate },
                    { title: 'Nhà cung cấp', dataIndex: ['supplier', 'name'] },
                    { title: 'Phiếu nhập', dataIndex: 'receiptCode', width: 140, render: (v, p) => <a onClick={() => nav(`/phieu-nhap/${p.receiptId}`)}>{v}</a> },
                    { title: 'Số tiền', dataIndex: 'amount', align: 'right', width: 130, render: (v) => <b>{fmtMoney(v)}</b> },
                    { title: 'Hình thức', dataIndex: 'method', width: 120 },
                    { title: 'Minh chứng', dataIndex: 'evidenceFile', render: (f) => <FileLink file={f} label="Xem" /> },
                    { title: 'Người ghi nhận', dataIndex: ['createdBy', 'fullName'], width: 150 },
                  ]} />
              </>
            ),
          },
        ]} />
      </Card>
      <PaymentModal open={!!paying} receipts={paying ?? []} onClose={() => setPaying(null)} onDone={() => { setPaying(null); reloadAll(); }} />
      <Space />
    </>
  );
}
