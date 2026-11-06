import { Alert, Button, Card, Col, Descriptions, InputNumber, Row, Skeleton, Space, Table, Tag } from 'antd';
import { EditOutlined, StopOutlined, SaveOutlined, WarningOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { DocStatusTag } from '../components/StatusTags';
import FileLink from '../components/FileLink';
import HistoryTimeline from '../components/HistoryTimeline';
import { askReason } from '../components/ReasonModal';
import { useFetch } from '../utils/useFetch';
import { api, showError } from '../api/client';
import type { Sales, SalesLine } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function SalesDetailPage() {
  const { id } = useParams();
  const { hasRole, canSeeValues } = useAuth();
  const { data: s, loading, reload } = useFetch<Sales>(`/sales/${id}`);
  const [editing, setEditing] = useState(false);
  const [qty, setQty] = useState<Record<string, number | null>>({});

  if (loading && !s) return <Skeleton active />;
  if (!s) return null;
  const active = s.status === 'ACTIVE';

  const startEdit = () => { setQty(Object.fromEntries((s.lines ?? []).map((l) => [l.id, l.quantity]))); setEditing(true); };
  const saveEdit = async () => {
    const changed = (s.lines ?? []).filter((l) => qty[l.id] !== null && qty[l.id] !== l.quantity);
    if (!changed.length) { ui.message.info('Không có thay đổi'); setEditing(false); return; }
    const reason = await askReason({ title: 'Lý do chỉnh sửa phiếu xuất', content: 'Tồn kho sẽ được hoàn lại và trừ lại theo số lượng mới.' });
    if (!reason) return;
    try {
      await api.put(`/sales/${s.id}`, { reason, lines: changed.map((l) => ({ lineId: l.id, quantity: qty[l.id] })) });
      ui.message.success('Đã cập nhật phiếu xuất');
      setEditing(false);
      reload();
    } catch (e) { showError(e); }
  };
  const cancel = async () => {
    const reason = await askReason({ title: `Hủy phiếu xuất ${s.code}?`, content: 'Lượng đã trừ sẽ được cộng lại vào tồn kho. Sau khi hủy có thể nhập lại doanh số của ngày này.', danger: true, okText: 'Hủy phiếu' });
    if (!reason) return;
    try { await api.post(`/sales/${s.id}/cancel`, { reason }); ui.message.success('Đã hủy phiếu xuất'); reload(); } catch (e) { showError(e); }
  };

  return (
    <>
      <PageHeader back="/phieu-xuat" title={`Phiếu xuất bán ${s.code}`} tags={<DocStatusTag status={s.status} />}
        subtitle={`Doanh số ngày ${fmtDate(s.salesDate)} · nhập bởi ${s.importedBy?.fullName ?? '—'} lúc ${fmtDateTime(s.importedAt)}`}
        extra={hasRole('MANAGER') && active && (editing ? <>
          <Button onClick={() => setEditing(false)}>Hủy chỉnh sửa</Button>
          <Button type="primary" icon={<SaveOutlined />} onClick={saveEdit}>Lưu thay đổi</Button>
        </> : <>
          <Button icon={<EditOutlined />} onClick={startEdit}>Sửa số lượng</Button>
          <Button danger icon={<StopOutlined />} onClick={cancel}>Hủy phiếu</Button>
        </>)} />
      {!active && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message={`Phiếu đã bị hủy bởi ${s.cancelledBy?.fullName ?? '—'}`} description={`Lý do: ${s.cancelReason ?? '—'}`} />}
      {active && s.overQuantity > 0 && <Alert type="error" showIcon icon={<WarningOutlined />} style={{ marginBottom: 16 }}
        message={`Có ${s.overQuantity} sản phẩm bán vượt tồn kho`} description="Tồn kho của các mặt hàng này đã được đặt về 0. Quản lý nên kiểm tra lại số liệu nhập hàng hoặc kiểm kê." />}
      <Row gutter={[16, 16]}>
        <Col xs={24} xl={17}>
          <Card className="section-card" title="Chi tiết bán hàng">
            <Table<SalesLine> size="small" rowKey="id" dataSource={s.lines ?? []} pagination={false} scroll={{ x: 820 }}
              rowClassName={(l) => (l.overQuantity > 0 ? 'row-danger' : '')}
              columns={[
                { title: 'Mã hàng', dataIndex: ['item', 'code'], width: 120 },
                { title: 'Mặt hàng', dataIndex: ['item', 'name'] },
                { title: 'SL bán', dataIndex: 'quantity', align: 'right', width: 110, render: (v, l) => (editing
                  ? <InputNumber min={0} size="small" value={qty[l.id] ?? undefined} onChange={(n) => setQty((q) => ({ ...q, [l.id]: n }))} style={{ width: 90 }} />
                  : <b>{v}</b>) },
                { title: 'Đã trừ kho', dataIndex: 'deductedQuantity', align: 'right', width: 100 },
                ...(canSeeValues ? [
                  { title: 'Đơn giá', dataIndex: 'unitPrice', align: 'right' as const, width: 110, render: fmtMoney },
                  { title: 'Thành tiền', dataIndex: 'amount', align: 'right' as const, width: 130, render: fmtMoney },
                ] : []),
                { title: 'Tồn trước → sau', width: 140, align: 'right', render: (_, l) => `${l.stockBefore} → ${l.stockAfter}` },
                { title: 'Vượt tồn', dataIndex: 'overQuantity', width: 100, render: (v: number) => (v > 0 ? <Tag color="error">{v}</Tag> : '—') },
              ]} />
          </Card>
        </Col>
        <Col xs={24} xl={7}>
          <Card className="section-card" title="Tổng hợp">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="Số mặt hàng">{s.lineCount}</Descriptions.Item>
              <Descriptions.Item label="Tổng số lượng">{fmtNumber(s.totalQuantity)}</Descriptions.Item>
              {canSeeValues && <Descriptions.Item label="Doanh thu"><b>{fmtMoney(s.totalAmount)}</b></Descriptions.Item>}
              <Descriptions.Item label="File POS gốc"><FileLink file={s.originalFile} /></Descriptions.Item>
            </Descriptions>
          </Card>
          <Card className="section-card" title="Lịch sử thay đổi"><HistoryTimeline items={s.history} /></Card>
        </Col>
      </Row>
      <Space />
    </>
  );
}
