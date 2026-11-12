import { Alert, Button, Card, Col, Descriptions, Row, Skeleton, Space, Table, Tag, Tooltip } from 'antd';
import { EditOutlined, StopOutlined, DollarOutlined, LockOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { DebtStatusTag, DocStatusTag } from '../components/StatusTags';
import FileLink from '../components/FileLink';
import HistoryTimeline from '../components/HistoryTimeline';
import { askReason } from '../components/ReasonModal';
import { PaymentEditModal, PaymentModal } from '../components/PaymentModal';
import { useFetch } from '../utils/useFetch';
import { api, showError } from '../api/client';
import type { Payment, Receipt, ReceiptLine } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtDateTime, fmtMoney } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function ReceiptDetailPage() {
  const { id } = useParams();
  const nav = useNavigate();
  const { hasRole, canSeeValues } = useAuth();
  const { data: r, loading, reload } = useFetch<Receipt>(`/receipts/${id}`);
  const [paying, setPaying] = useState(false);
  const [editingPay, setEditingPay] = useState<Payment | null>(null);

  if (loading && !r) return <Skeleton active />;
  if (!r) return null;
  const activePayments = (r.payments ?? []).filter((p) => p.status === 'ACTIVE');
  const hasPayments = activePayments.length > 0;
  const active = r.status === 'ACTIVE';

  const cancel = async () => {
    const reason = await askReason({ title: `Hủy phiếu nhập ${r.code}?`, content: 'Tồn kho của các mặt hàng sẽ được giảm lại tương ứng. Thao tác không thể hoàn tác.', okText: 'Hủy phiếu', danger: true });
    if (!reason) return;
    try { await api.post(`/receipts/${r.id}/cancel`, { reason }); ui.message.success('Đã hủy phiếu nhập'); reload(); } catch (e) { showError(e); }
  };

  const cancelPayment = async (p: Payment) => {
    const reason = await askReason({ title: `Hủy khoản thanh toán ${p.code}?`, content: `Số tiền ${fmtMoney(p.amount)} sẽ được cộng lại vào công nợ.`, okText: 'Hủy thanh toán', danger: true });
    if (!reason) return;
    try { await api.post(`/payments/${p.id}/cancel`, { reason }); ui.message.success('Đã hủy khoản thanh toán'); reload(); } catch (e) { showError(e); }
  };

  return (
    <>
      <PageHeader back="/phieu-nhap" title={`Phiếu nhập ${r.code}`}
        tags={<>{active ? <DocStatusTag status={r.status} /> : <DocStatusTag status="CANCELLED" />}{canSeeValues && active && <DebtStatusTag status={r.debtStatus} overdue={r.overdue} />}</>}
        subtitle={`${r.supplier.name} · Hóa đơn ${r.invoiceNo} · Ngày nhập ${fmtDate(r.receiptDate)}`}
        extra={<>
          {hasRole('ACCOUNTANT') && active && (r.remainingAmount ?? 0) > 0 && (
            <Button type="primary" icon={<DollarOutlined />} onClick={() => setPaying(true)}>Thanh toán</Button>
          )}
          {hasRole('MANAGER') && active && (
            <>
              <Tooltip title={hasPayments ? 'Phiếu đã có khoản thanh toán – cần Kế toán hủy thanh toán trước' : undefined}>
                <Button icon={<EditOutlined />} disabled={hasPayments} onClick={() => nav(`/phieu-nhap/${r.id}/sua`)}>Sửa phiếu</Button>
              </Tooltip>
              <Tooltip title={hasPayments ? 'Phiếu đã có khoản thanh toán – cần Kế toán hủy thanh toán trước' : undefined}>
                <Button danger icon={<StopOutlined />} disabled={hasPayments} onClick={cancel}>Hủy phiếu</Button>
              </Tooltip>
            </>
          )}
        </>} />

      {!active && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={`Phiếu đã bị hủy bởi ${r.cancelledBy?.fullName ?? '—'}`} description={`Lý do: ${r.cancelReason ?? '—'}`} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={16}>
          <Card className="section-card" title="Thông tin phiếu">
            <Descriptions size="small" column={{ xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2 }} bordered className="kv-grid">
              <Descriptions.Item label="Nhà cung cấp">{r.supplier.name}</Descriptions.Item>
              <Descriptions.Item label="Số hóa đơn">{r.invoiceNo}</Descriptions.Item>
              <Descriptions.Item label="Ngày nhập">{fmtDate(r.receiptDate)}</Descriptions.Item>
              <Descriptions.Item label="Hạn thanh toán">{fmtDate(r.dueDate)}{r.overdue && <Tag color="error" style={{ marginLeft: 8 }}>Quá hạn</Tag>}</Descriptions.Item>
              <Descriptions.Item label="Người lập">{r.createdBy?.fullName} · {fmtDateTime(r.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="Tệp hóa đơn"><FileLink file={r.invoiceFile} /></Descriptions.Item>
              {canSeeValues && <Descriptions.Item label="Đối soát công nợ">
                {r.reconcileStatus === 'NONE' ? 'Chưa đối soát' : r.reconcileStatus === 'MATCHED' ? <Tag color="success">Đã khớp</Tag> : <Tag color="error">{r.reconcileNote ?? 'Chênh lệch'}</Tag>}
              </Descriptions.Item>}
              <Descriptions.Item label="Ghi chú">{r.note ?? '—'}</Descriptions.Item>
            </Descriptions>
          </Card>
          <Card className="section-card" title={`Mặt hàng (${r.lineCount})`}>
            <Table<ReceiptLine> size="small" rowKey="id" dataSource={r.lines ?? []} pagination={false} scroll={{ x: 600 }}
              columns={[
                { title: '#', width: 40, render: (_, __, i) => i + 1 },
                { title: 'Mã hàng', dataIndex: ['item', 'code'], width: 120 },
                { title: 'Tên mặt hàng', dataIndex: ['item', 'name'] },
                { title: 'ĐVT', dataIndex: 'unit', width: 70 },
                { title: 'Số lượng', dataIndex: 'quantity', align: 'right', width: 90 },
                { title: 'Đơn giá', dataIndex: 'unitPrice', align: 'right', width: 120, render: fmtMoney },
                { title: 'Thành tiền', dataIndex: 'lineAmount', align: 'right', width: 140, render: (v) => <b>{fmtMoney(v)}</b> },
              ]}
              summary={() => (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={4}><b>Tổng cộng</b></Table.Summary.Cell>
                  <Table.Summary.Cell index={4} align="right"><b>{r.totalQuantity}</b></Table.Summary.Cell>
                  <Table.Summary.Cell index={5} />
                  <Table.Summary.Cell index={6} align="right"><b>{fmtMoney(r.totalAmount)}</b></Table.Summary.Cell>
                </Table.Summary.Row>
              )} />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          {canSeeValues && (
            <Card className="section-card" title="Công nợ">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Tổng tiền">{fmtMoney(r.totalAmount)}</Descriptions.Item>
                <Descriptions.Item label="Đã thanh toán"><span style={{ color: '#008300' }}>{fmtMoney(r.paidAmount)}</span></Descriptions.Item>
                <Descriptions.Item label="Còn nợ"><b style={{ fontSize: 18 }}>{fmtMoney(r.remainingAmount)}</b></Descriptions.Item>
              </Descriptions>
            </Card>
          )}
          <Card className="section-card" title="Lịch sử thay đổi"><HistoryTimeline items={r.history} /></Card>
        </Col>
      </Row>

      {canSeeValues && (
        <Card className="section-card" title="Các khoản thanh toán" style={{ marginTop: 16 }}>
          <Table<Payment> size="small" rowKey="id" dataSource={r.payments ?? []} pagination={false} scroll={{ x: 900 }}
            locale={{ emptyText: 'Chưa có khoản thanh toán nào' }}
            rowClassName={(p) => (p.status === 'CANCELLED' ? 'row-muted' : '')}
            columns={[
              { title: 'Mã', dataIndex: 'code', width: 130 },
              { title: 'Ngày TT', dataIndex: 'paidAt', width: 100, render: fmtDate },
              { title: 'Số tiền', dataIndex: 'amount', align: 'right', width: 130, render: (v) => <b>{fmtMoney(v)}</b> },
              { title: 'Hình thức', dataIndex: 'method', width: 120 },
              { title: 'Minh chứng', dataIndex: 'evidenceFile', render: (f) => <FileLink file={f} label="Xem minh chứng" /> },
              { title: 'Người ghi nhận', dataIndex: ['createdBy', 'fullName'], width: 150 },
              { title: 'Trạng thái', width: 160, render: (_, p) => (p.status === 'CANCELLED'
                ? <Tooltip title={p.cancelReason}><Tag>Đã hủy</Tag></Tooltip>
                : p.locked ? <Tag icon={<LockOutlined />} color="purple">Kỳ đã đối soát</Tag> : <Tag color="success">Hiệu lực</Tag>) },
              ...(hasRole('ACCOUNTANT') ? [{
                title: 'Thao tác', width: 160, render: (_: unknown, p: Payment) => p.status === 'ACTIVE' && (
                  <Space>
                    <Button size="small" icon={<EditOutlined />} disabled={p.locked} onClick={() => setEditingPay(p)}>Sửa</Button>
                    <Button size="small" danger disabled={p.locked} onClick={() => cancelPayment(p)}>Hủy</Button>
                  </Space>
                ),
              }] : []),
            ]} />
          {(r.payments ?? []).some((p) => p.locked) && (
            <div className="muted small" style={{ marginTop: 8 }}>
              Khoản thanh toán thuộc kỳ đã đối soát hoàn tất sẽ bị khóa. Mở lại kỳ ở mục Đối soát công nợ để chỉnh sửa.
            </div>
          )}
        </Card>
      )}

      <PaymentModal open={paying} receipts={[r]} onClose={() => setPaying(false)} onDone={() => { setPaying(false); reload(); }} />
      <PaymentEditModal payment={editingPay} maxAmount={(r.remainingAmount ?? 0) + (editingPay?.amount ?? 0)}
        onClose={() => setEditingPay(null)} onDone={() => { setEditingPay(null); reload(); }} />
    </>
  );
}
