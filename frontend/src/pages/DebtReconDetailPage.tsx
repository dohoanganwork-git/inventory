import { Alert, Button, Card, Col, Input, Modal, Row, Segmented, Skeleton, Space, Table, Tabs } from 'antd';
import { CheckCircleOutlined, UnlockOutlined, UploadOutlined, SyncOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import StatCard from '../components/StatCard';
import FileLink from '../components/FileLink';
import UploadField from '../components/UploadField';
import HistoryTimeline from '../components/HistoryTimeline';
import { ReconResultTag, ReconStatusTag } from '../components/StatusTags';
import { askReason } from '../components/ReasonModal';
import { useFetch } from '../utils/useFetch';
import { api, showError } from '../api/client';
import type { DebtRecon, DebtReconLine } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtDateTime, fmtMoney } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function DebtReconDetailPage() {
  const { id } = useParams();
  const nav = useNavigate();
  const { hasRole } = useAuth();
  const { data: d, loading, setData } = useFetch<DebtRecon>(`/debt-reconciliations/${id}`);
  const [filter, setFilter] = useState<'all' | 'bad'>('bad');
  const [reupload, setReupload] = useState(false);
  const [invoiceFile, setInvoiceFile] = useState<File | null>(null);
  const [evidenceFile, setEvidenceFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [notes, setNotes] = useState<Record<string, string>>({});

  if (loading && !d) return <Skeleton active />;
  if (!d) return null;
  const editable = hasRole('ACCOUNTANT') && d.status === 'IN_PROGRESS';
  const lines = (d.lines ?? []).filter((l) => filter === 'all' || l.result !== 'MATCHED');

  const saveNote = async (l: DebtReconLine) => {
    const note = notes[l.id];
    if (note === undefined || note === (l.note ?? '')) return;
    try {
      const { data } = await api.patch<DebtRecon>(`/debt-reconciliations/${d.id}/lines/${l.id}`, { note });
      setData(data);
      ui.message.success('Đã lưu ghi chú');
    } catch (e) { showError(e); }
  };

  const doReupload = async () => {
    if (!invoiceFile) { ui.message.warning('Vui lòng chọn file hóa đơn'); return; }
    const fd = new FormData();
    fd.append('invoiceFile', invoiceFile);
    if (evidenceFile) fd.append('evidenceFile', evidenceFile);
    setBusy(true);
    try {
      const { data } = await api.post<DebtRecon>(`/debt-reconciliations/${d.id}/reupload`, fd);
      setData(data);
      setReupload(false);
      ui.message.success(`Đã đối chiếu lại: ${data.matchedCount} khớp, ${data.mismatchCount} chênh lệch`);
    } catch (e) { showError(e); } finally { setBusy(false); }
  };

  const rematch = async () => {
    setBusy(true);
    try {
      const { data } = await api.post<DebtRecon>(`/debt-reconciliations/${d.id}/rematch`);
      setData(data);
      ui.message.success(`Đã đối chiếu lại với dữ liệu hiện tại: ${data.mismatchCount} chênh lệch`);
    } catch (e) { showError(e); } finally { setBusy(false); }
  };

  const complete = async () => {
    const note = await askReason({ title: 'Xác nhận hoàn tất đối soát', content: 'Kỳ đối soát sẽ bị khóa; các khoản thanh toán trong kỳ không thể chỉnh sửa trừ khi mở lại kỳ.',
      label: 'Ghi chú (không bắt buộc)', required: false, okText: 'Hoàn tất & khóa kỳ' });
    if (note === null) return;
    try { const { data } = await api.post<DebtRecon>(`/debt-reconciliations/${d.id}/complete`, { note }); setData(data); ui.message.success('Kỳ đối soát đã hoàn tất'); } catch (e) { showError(e); }
  };

  const reopen = async () => {
    const reason = await askReason({ title: 'Mở lại kỳ đối soát?', content: 'Cho phép chỉnh sửa các khoản thanh toán thuộc kỳ này.', okText: 'Mở lại kỳ' });
    if (!reason) return;
    try { const { data } = await api.post<DebtRecon>(`/debt-reconciliations/${d.id}/reopen`, { reason }); setData(data); ui.message.success('Đã mở lại kỳ đối soát'); } catch (e) { showError(e); }
  };

  return (
    <>
      <PageHeader back="/doi-soat-cong-no" title={`Đối soát ${d.code}`} tags={<ReconStatusTag status={d.status} />}
        subtitle={`${d.supplier.name} · Kỳ ${fmtDate(d.periodFrom)} – ${fmtDate(d.periodTo)}`}
        extra={<>
          {editable && <Button icon={<SyncOutlined />} loading={busy} onClick={rematch}>Đối chiếu lại</Button>}
          {editable && <Button icon={<UploadOutlined />} onClick={() => { setInvoiceFile(null); setEvidenceFile(null); setReupload(true); }}>Tải lại file</Button>}
          {editable && <Button type="primary" icon={<CheckCircleOutlined />} onClick={complete} disabled={d.unresolvedCount > 0}>Xác nhận hoàn tất</Button>}
          {hasRole('ACCOUNTANT') && d.status === 'COMPLETED' && <Button icon={<UnlockOutlined />} onClick={reopen}>Mở lại kỳ</Button>}
        </>} />
      {d.status === 'IN_PROGRESS' && d.unresolvedCount > 0 && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }}
          message={`Còn ${d.unresolvedCount} mục chênh lệch chưa xử lý`}
          description="Xử lý từng mục (chỉnh sửa khoản thanh toán, liên hệ nhà cung cấp...) rồi tải lại file hoặc bấm Đối chiếu lại; hoặc ghi chú cách xử lý cho từng mục để có thể hoàn tất kỳ." />
      )}
      {d.status === 'COMPLETED' && (
        <Alert type="success" showIcon style={{ marginBottom: 16 }} message={`Hoàn tất bởi ${d.completedBy?.fullName ?? '—'} lúc ${fmtDateTime(d.completedAt)}`} description={d.note} />
      )}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}><StatCard title="Khớp" value={d.matchedCount} tone="success" /></Col>
        <Col xs={12} md={6}><StatCard title="Chênh lệch" value={d.mismatchCount} tone={d.mismatchCount ? 'danger' : 'success'} hint={`${d.unresolvedCount} chưa xử lý`} /></Col>
        <Col xs={12} md={6}><StatCard title="Tổng theo file NCC" value={fmtMoney(d.fileTotal)} /></Col>
        <Col xs={12} md={6}><StatCard title="Tổng theo hệ thống" value={fmtMoney(d.systemTotal)} hint={`Đã trả ${fmtMoney(d.systemPaid)}`} /></Col>
      </Row>
      <Card className="section-card">
        <Tabs items={[
          {
            key: 'lines', label: 'Kết quả đối chiếu', children: (
              <>
                <div className="filter-bar">
                  <Segmented value={filter} onChange={(v) => setFilter(v as typeof filter)}
                    options={[{ label: `Chênh lệch (${d.mismatchCount})`, value: 'bad' }, { label: `Tất cả (${d.lines?.length ?? 0})`, value: 'all' }]} />
                </div>
                <Table<DebtReconLine> size="small" rowKey="id" dataSource={lines} pagination={false} scroll={{ x: 1100 }}
                  locale={{ emptyText: 'Không có mục chênh lệch' }}
                  rowClassName={(l) => (l.result !== 'MATCHED' && !l.note ? 'row-danger' : '')}
                  columns={[
                    { title: 'Số hóa đơn', dataIndex: 'invoiceNo', width: 140, render: (v) => <b>{v}</b> },
                    { title: 'Ngày', dataIndex: 'invoiceDate', width: 100, render: fmtDate },
                    { title: 'Phiếu nhập', dataIndex: 'receiptCode', width: 140, render: (v, l) => (v ? <a onClick={() => nav(`/phieu-nhap/${l.receiptId}`)}>{v}</a> : '—') },
                    { title: 'Tiền theo file', dataIndex: 'fileAmount', align: 'right', width: 130, render: fmtMoney },
                    { title: 'Tiền hệ thống', dataIndex: 'systemAmount', align: 'right', width: 130, render: fmtMoney },
                    { title: 'Đã trả (file)', dataIndex: 'filePaid', align: 'right', width: 120, render: fmtMoney },
                    { title: 'Đã trả (HT)', dataIndex: 'systemPaid', align: 'right', width: 120, render: fmtMoney },
                    { title: 'Kết quả', dataIndex: 'result', width: 180, render: (v) => <ReconResultTag result={v} /> },
                    { title: 'Ghi chú xử lý', width: 260, render: (_, l) => (l.result === 'MATCHED' ? null : editable
                      ? <Input.TextArea autoSize={{ minRows: 1, maxRows: 3 }} placeholder="Cách xử lý chênh lệch..." defaultValue={l.note}
                          onChange={(e) => setNotes((n) => ({ ...n, [l.id]: e.target.value }))} onBlur={() => saveNote(l)} />
                      : l.note ?? '—') },
                  ]} />
              </>
            ),
          },
          {
            key: 'files', label: 'File đã tải lên', children: (
              <Table size="small" rowKey="id" dataSource={d.uploads ?? []} pagination={false}
                columns={[
                  { title: 'Thời gian', dataIndex: 'uploadedAt', render: fmtDateTime },
                  { title: 'File hóa đơn', dataIndex: 'invoiceFile', render: (f) => <FileLink file={f} /> },
                  { title: 'Minh chứng', dataIndex: 'evidenceFile', render: (f) => <FileLink file={f} /> },
                  { title: 'Người tải', dataIndex: ['uploadedBy', 'fullName'] },
                ]} />
            ),
          },
          { key: 'history', label: 'Lịch sử', children: <HistoryTimeline items={d.history} /> },
        ]} />
      </Card>
      <Modal open={reupload} title="Tải lại file để đối chiếu" onOk={doReupload} onCancel={() => setReupload(false)} okText="Đối chiếu lại" cancelText="Hủy" confirmLoading={busy} destroyOnHidden>
        <Space direction="vertical" style={{ width: '100%' }}>
          <div>File hóa đơn đã chỉnh sửa:</div>
          <UploadField value={invoiceFile} onChange={setInvoiceFile} accept=".xlsx,.xls,.csv" />
          <div>Minh chứng thanh toán (không bắt buộc):</div>
          <UploadField value={evidenceFile} onChange={setEvidenceFile} accept=".pdf,.jpg,.jpeg,.png,.webp" dragger={false} label="Chọn minh chứng" />
        </Space>
      </Modal>
      <Space />
    </>
  );
}
