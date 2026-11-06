import { Alert, Button, Card, Col, Descriptions, Input, Modal, Radio, Row, Segmented, Skeleton, Space, Table, Tabs, Tag, Tooltip, Typography } from 'antd';
import { CheckOutlined, SyncOutlined, UploadOutlined, DownloadOutlined, InfoCircleOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import FileLink from '../components/FileLink';
import UploadField from '../components/UploadField';
import HistoryTimeline from '../components/HistoryTimeline';
import { StocktakeStatusTag, VarianceText } from '../components/StatusTags';
import { useFetch } from '../utils/useFetch';
import { api, download, showError } from '../api/client';
import type { Stocktake, StocktakeLine } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtDateTime } from '../utils/format';
import { submissionLabel } from '../utils/labels';
import { useAuth } from '../auth/AuthContext';

type Choice = 'APPROVE' | 'RECOUNT';

const DecisionTag = ({ l }: { l: StocktakeLine }) => {
  if (l.decision === 'APPROVED') return <Tag color="success" icon={<CheckOutlined />}>Đã điều chỉnh{l.adjustedQty !== undefined && l.adjustedQty !== null ? ` → ${l.adjustedQty}` : ''}</Tag>;
  if (l.decision === 'RECOUNT') return <Tag color="processing" icon={<SyncOutlined />}>Chờ kiểm kê lại</Tag>;
  if (l.needsDecision) return <Tag color="warning">Chờ quyết định</Tag>;
  return <Tag>Khớp</Tag>;
};

export default function StocktakeDetailPage() {
  const { id } = useParams();
  const { hasRole } = useAuth();
  const { data: s, loading, setData } = useFetch<Stocktake>(`/stocktakes/${id}`);
  const [filter, setFilter] = useState<'diff' | 'all'>('diff');
  const [choices, setChoices] = useState<Record<string, Choice>>({});
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [resubmit, setResubmit] = useState(false);
  const [countFile, setCountFile] = useState<File | null>(null);
  const [damageFile, setDamageFile] = useState<File | null>(null);

  const pending = useMemo(() => (s?.lines ?? []).filter((l) => l.decision === 'NONE' && l.needsDecision), [s]);
  if (loading && !s) return <Skeleton active />;
  if (!s) return null;

  const isManager = hasRole('MANAGER');
  const canUpload = hasRole('STAFF', 'MANAGER');
  const deciding = isManager && s.status === 'PENDING_REVIEW';
  const lines = (s.lines ?? []).filter((l) => filter === 'all' || l.needsDecision || l.decision !== 'NONE' || l.initialVarianceQty !== 0);
  const recountItems = (s.lines ?? []).filter((l) => l.decision === 'RECOUNT');

  const setAll = (c: Choice) => setChoices(Object.fromEntries(pending.map((l) => [l.id, c])));

  const decide = async () => {
    const missing = pending.filter((l) => !choices[l.id]);
    if (missing.length) { ui.message.warning(`Còn ${missing.length} mặt hàng chưa chọn quyết định`); return; }
    if (!reason.trim()) { ui.message.warning('Vui lòng nhập lý do'); return; }
    const approve = pending.filter((l) => choices[l.id] === 'APPROVE').map((l) => l.id);
    const recount = pending.filter((l) => choices[l.id] === 'RECOUNT').map((l) => l.id);
    ui.modal.confirm({
      title: 'Xác nhận quyết định',
      content: <>
        {approve.length > 0 && <p>• Điều chỉnh tồn kho theo số thực tế cho <b>{approve.length}</b> mặt hàng (ghi nhận hỏng/hủy nếu có).</p>}
        {recount.length > 0 && <p>• Yêu cầu Nhân viên kiểm kê lại <b>{recount.length}</b> mặt hàng (tồn kho của các mặt hàng này chưa thay đổi).</p>}
      </>,
      okText: 'Xác nhận', cancelText: 'Xem lại',
      onOk: async () => {
        setSubmitting(true);
        try {
          const { data } = await api.post<Stocktake>(`/stocktakes/${s.id}/decision`, { approveLineIds: approve, recountLineIds: recount, reason });
          setData(data);
          setChoices({});
          setReason('');
          ui.message.success(data.status === 'COMPLETED' ? 'Đã điều chỉnh tồn kho và khóa đợt kiểm kê' : 'Đã gửi yêu cầu kiểm kê lại cho Nhân viên');
        } catch (e) { showError(e); } finally { setSubmitting(false); }
      },
    });
  };

  const doResubmit = async () => {
    if (!countFile) { ui.message.warning('Vui lòng chọn file kiểm kê'); return; }
    const fd = new FormData();
    fd.append('countFile', countFile);
    if (damageFile) fd.append('damageFile', damageFile);
    setSubmitting(true);
    try {
      const { data } = await api.post<Stocktake>(`/stocktakes/${s.id}/resubmit`, fd);
      setData(data);
      setResubmit(false);
      ui.message.success(data.status === 'COMPLETED' ? 'Kết quả khớp – đợt kiểm kê hoàn tất' : 'Đã gửi lại file, chờ Quản lý xem xét');
    } catch (e) { showError(e); } finally { setSubmitting(false); }
  };

  return (
    <>
      <PageHeader back="/kiem-ke" title={`Đợt kiểm kê ${s.code}`} tags={<StocktakeStatusTag status={s.status} />}
        subtitle={`Ngày kiểm kê ${fmtDate(s.stocktakeDate)} · Lần kiểm thứ ${s.round} · Gửi bởi ${s.createdBy?.fullName ?? '—'}`}
        extra={canUpload && s.status !== 'COMPLETED' && (
          <Button icon={<UploadOutlined />} type={s.status === 'RECOUNT_REQUESTED' ? 'primary' : 'default'}
            onClick={() => { setCountFile(null); setDamageFile(null); setResubmit(true); }}>
            {s.status === 'RECOUNT_REQUESTED' ? 'Gửi file kiểm kê lại' : 'Thay file (nhập nhầm)'}
          </Button>
        )} />

      {s.status === 'PENDING_REVIEW' && !isManager && (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} message="Đang chờ Quản lý xem xét kết quả đối chiếu"
          description="Nếu phát hiện nhập nhầm, bạn có thể thay file trước khi Quản lý xử lý. File cũ vẫn được lưu trong lịch sử." />
      )}
      {s.status === 'RECOUNT_REQUESTED' && (
        <Alert type={isManager ? 'info' : 'error'} showIcon style={{ marginBottom: 16 }}
          message={isManager ? `Đang chờ Nhân viên kiểm kê lại ${recountItems.length} mặt hàng` : `Quản lý yêu cầu kiểm kê lại ${recountItems.length} mặt hàng`}
          description={<>
            <div>Lý do: {s.recountReason}</div>
            <div style={{ marginTop: 4 }}>Mặt hàng: {recountItems.map((l) => `${l.item.code} – ${l.item.name}`).join('; ')}</div>
            {!isManager && <div style={{ marginTop: 4 }}>File kiểm kê lại chỉ gồm các mặt hàng trên (có thể dùng file mẫu và xóa các dòng khác).</div>}
          </>} />
      )}
      {s.status === 'COMPLETED' && (
        <Alert type="success" showIcon style={{ marginBottom: 16 }} message="Đợt kiểm kê đã hoàn tất và được khóa"
          description={<div style={{ whiteSpace: 'pre-line' }}>{s.reviewedBy ? `Người duyệt: ${s.reviewedBy.fullName} · ${fmtDateTime(s.decidedAt)}\n` : ''}{s.decisionReason}</div>} />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}><StatCard title="Mặt hàng kiểm kê" value={s.totalLines} /></Col>
        <Col xs={12} md={6}><StatCard title="Khớp lần đầu" value={s.matchedLines} tone="success" hint={`Tỉ lệ chính xác ${s.accuracy}%`} /></Col>
        <Col xs={12} md={6}><StatCard title="Chênh lệch" value={s.varianceLines} tone={s.varianceLines ? 'danger' : 'success'} hint={`${s.pendingLines} chờ quyết định`} /></Col>
        <Col xs={12} md={6}><StatCard title="Hỏng / hủy" value={s.damagedQty} tone={s.damagedQty ? 'warning' : 'success'} hint="đơn vị sản phẩm" /></Col>
      </Row>

      <Card className="section-card">
        <Tabs items={[
          {
            key: 'lines', label: 'Kết quả đối chiếu', children: (
              <>
                <div className="filter-bar" style={{ justifyContent: 'space-between' }}>
                  <Segmented value={filter} onChange={(v) => setFilter(v as typeof filter)}
                    options={[{ label: 'Chênh lệch & hỏng/hủy', value: 'diff' }, { label: `Tất cả (${s.lines?.length ?? 0})`, value: 'all' }]} />
                  {deciding && pending.length > 0 && (
                    <Space wrap>
                      <span className="muted">Chọn nhanh cho {pending.length} mặt hàng:</span>
                      <Button size="small" onClick={() => setAll('APPROVE')}>Đồng ý tất cả</Button>
                      <Button size="small" onClick={() => setAll('RECOUNT')}>Kiểm kê lại tất cả</Button>
                    </Space>
                  )}
                </div>
                <Table<StocktakeLine> size="small" rowKey="id" dataSource={lines} pagination={false} scroll={{ x: 1100 }}
                  locale={{ emptyText: 'Không có mặt hàng chênh lệch' }}
                  rowClassName={(l) => (l.decision === 'NONE' && l.needsDecision ? 'row-warn' : '')}
                  columns={[
                    { title: 'Mã hàng', dataIndex: ['item', 'code'], width: 120 },
                    { title: 'Mặt hàng', dataIndex: ['item', 'name'] },
                    { title: <Tooltip title="Tồn kho trên hệ thống tại thời điểm đối chiếu (đã trừ doanh số POS)">Tồn sổ sách <InfoCircleOutlined /></Tooltip>, dataIndex: 'bookQty', width: 110, align: 'right' },
                    { title: 'Hỏng/hủy', dataIndex: 'damagedQty', width: 100, align: 'right', render: (v, l) => (v ? <Tooltip title={l.damageReason}><Tag color="warning">{v}</Tag></Tooltip> : '—') },
                    { title: 'Tồn dự kiến', dataIndex: 'expectedQty', width: 100, align: 'right' },
                    { title: 'Thực tế', dataIndex: 'countedQty', width: 90, align: 'right', render: (v) => <b>{v}</b> },
                    { title: 'Chênh lệch', dataIndex: 'varianceQty', width: 130, render: (v) => <VarianceText v={v} /> },
                    { title: 'Kiểm lại', dataIndex: 'recountTimes', width: 80, align: 'right', render: (v) => (v ? `${v} lần` : '—') },
                    deciding ? {
                      title: 'Quyết định của Quản lý', width: 260, render: (_: unknown, l: StocktakeLine) => (l.decision === 'NONE' && l.needsDecision
                        ? <Radio.Group size="small" optionType="button" buttonStyle="solid" value={choices[l.id]}
                            onChange={(e) => setChoices((c) => ({ ...c, [l.id]: e.target.value }))}
                            options={[{ value: 'APPROVE', label: 'Đồng ý điều chỉnh' }, { value: 'RECOUNT', label: 'Kiểm kê lại' }]} />
                        : <DecisionTag l={l} />),
                    } : { title: 'Kết quả', width: 180, render: (_: unknown, l: StocktakeLine) => <DecisionTag l={l} /> },
                  ]} />
                {deciding && pending.length > 0 && (
                  <div style={{ marginTop: 16, padding: 16, background: '#f6f8fb', borderRadius: 8 }}>
                    <Typography.Text strong>Lý do quyết định (bắt buộc)</Typography.Text>
                    <Input.TextArea rows={2} maxLength={1000} value={reason} onChange={(e) => setReason(e.target.value)} style={{ margin: '8px 0 12px' }}
                      placeholder="Ví dụ: Đã kiểm tra camera, xác nhận chênh lệch do bán chưa ghi nhận / Nghi ngờ đếm sai, cần kiểm lại..." />
                    <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
                      <span className="muted">Đã chọn {Object.keys(choices).filter((k) => pending.some((l) => l.id === k)).length}/{pending.length} mặt hàng ·
                        Đồng ý: {pending.filter((l) => choices[l.id] === 'APPROVE').length} · Kiểm kê lại: {pending.filter((l) => choices[l.id] === 'RECOUNT').length}</span>
                      <Button type="primary" size="large" loading={submitting} onClick={decide}>Xác nhận quyết định</Button>
                    </Space>
                  </div>
                )}
              </>
            ),
          },
          {
            key: 'files', label: `File đã gửi (${s.submissions?.length ?? 0})`, children: (
              <Table size="small" rowKey="id" dataSource={s.submissions ?? []} pagination={false} scroll={{ x: 700 }}
                columns={[
                  { title: 'Thời gian', dataIndex: 'submittedAt', width: 150, render: fmtDateTime },
                  { title: 'Loại', dataIndex: 'kind', width: 120, render: (v) => submissionLabel[v] ?? v },
                  { title: 'File tồn thực tế', dataIndex: 'countFile', render: (f) => <FileLink file={f} /> },
                  { title: 'File hỏng/hủy', dataIndex: 'damageFile', render: (f) => <FileLink file={f} /> },
                  { title: 'MH cần quyết định', dataIndex: 'varianceLines', width: 140, align: 'right' },
                  { title: 'Người gửi', dataIndex: ['submittedBy', 'fullName'], width: 150 },
                ]} />
            ),
          },
          {
            key: 'info', label: 'Thông tin & lịch sử', children: (
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Descriptions bordered size="small" column={1}>
                    <Descriptions.Item label="Ngày kiểm kê">{fmtDate(s.stocktakeDate)}</Descriptions.Item>
                    <Descriptions.Item label="Người gửi">{s.createdBy?.fullName}</Descriptions.Item>
                    <Descriptions.Item label="Người duyệt">{s.reviewedBy?.fullName ?? '—'}</Descriptions.Item>
                    <Descriptions.Item label="Quyết định"><div style={{ whiteSpace: 'pre-line' }}>{s.decisionReason ?? '—'}</div></Descriptions.Item>
                  </Descriptions>
                </Col>
                <Col xs={24} md={12}><HistoryTimeline items={s.history} /></Col>
              </Row>
            ),
          },
        ]} />
      </Card>

      <Modal open={resubmit} title={s.status === 'RECOUNT_REQUESTED' ? 'Gửi file kiểm kê lại' : 'Thay file kiểm kê'} onOk={doResubmit}
        onCancel={() => setResubmit(false)} okText="Gửi & đối chiếu lại" cancelText="Hủy" confirmLoading={submitting} width={640} destroyOnHidden>
        {s.status === 'RECOUNT_REQUESTED'
          ? <Alert type="info" showIcon style={{ marginBottom: 12 }} message={`File chỉ gồm ${recountItems.length} mặt hàng cần kiểm kê lại: ${recountItems.map((l) => l.item.code).join(', ')}`} />
          : <Alert type="info" showIcon style={{ marginBottom: 12 }} message="File mới sẽ thay thế kết quả đối chiếu hiện tại; file cũ vẫn được lưu trong lịch sử." />}
        <Space style={{ marginBottom: 12 }}>
          <Button size="small" icon={<DownloadOutlined />} onClick={() => download('/templates/stocktake')}>Mẫu kiểm kê</Button>
          <Button size="small" icon={<DownloadOutlined />} onClick={() => download('/templates/damage')}>Mẫu hỏng/hủy</Button>
        </Space>
        <Row gutter={12}>
          <Col xs={24} sm={12}><div style={{ marginBottom: 6 }}>File tồn thực tế *</div><UploadField value={countFile} onChange={setCountFile} accept=".xlsx,.xls,.csv" /></Col>
          <Col xs={24} sm={12}><div style={{ marginBottom: 6 }}>File hỏng/hủy</div><UploadField value={damageFile} onChange={setDamageFile} accept=".xlsx,.xls,.csv" /></Col>
        </Row>
      </Modal>
    </>
  );
}
