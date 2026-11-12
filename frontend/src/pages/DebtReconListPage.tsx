import { Alert, Button, Card, DatePicker, Form, Modal, Select, Space, Table } from 'antd';
import { PlusOutlined, DownloadOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { ReconStatusTag } from '../components/StatusTags';
import UploadField from '../components/UploadField';
import { useFetch } from '../utils/useFetch';
import { api, download, showError } from '../api/client';
import type { DebtRecon, Supplier } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtDateTime, fmtMoney, toIso } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function DebtReconListPage() {
  const { hasRole } = useAuth();
  const nav = useNavigate();
  const { data, loading } = useFetch<DebtRecon[]>('/debt-reconciliations');
  const { data: suppliers } = useFetch<Supplier[]>('/suppliers');
  const [open, setOpen] = useState(false);
  const [invoiceFile, setInvoiceFile] = useState<File | null>(null);
  const [evidenceFile, setEvidenceFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const create = async () => {
    const v = await form.validateFields().catch(() => null);
    if (!v) return;
    if (!invoiceFile) { ui.message.warning('Vui lòng chọn file hóa đơn của nhà cung cấp'); return; }
    const fd = new FormData();
    fd.append('invoiceFile', invoiceFile);
    if (evidenceFile) fd.append('evidenceFile', evidenceFile);
    setSaving(true);
    try {
      const { data: d } = await api.post<DebtRecon>('/debt-reconciliations', fd, {
        params: { supplierId: v.supplierId, periodFrom: toIso(v.period[0]), periodTo: toIso(v.period[1]) },
      });
      ui.message.success(`Đã đối chiếu: ${d.matchedCount} khớp, ${d.mismatchCount} chênh lệch`);
      nav(`/doi-soat-cong-no/${d.id}`);
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  return (
    <>
      <PageHeader title="Đối soát công nợ"
        subtitle="Hệ thống không kết nối ngân hàng: Kế toán tải bảng kê hóa đơn của nhà cung cấp theo kỳ, hệ thống đối chiếu với phiếu nhập và khoản thanh toán đã ghi nhận"
        extra={hasRole('ACCOUNTANT') && <>
          <Button icon={<DownloadOutlined />} onClick={() => download('/templates/debt-invoice')}>File mẫu</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.resetFields(); setInvoiceFile(null); setEvidenceFile(null); setOpen(true); }}>Tạo kỳ đối soát</Button>
        </>} />
      <Card className="section-card">
        <Table<DebtRecon> rowKey="id" loading={loading} dataSource={data ?? []} scroll={{ x: 1000 }}
          rowClassName="row-clickable" onRow={(r) => ({ onClick: () => nav(`/doi-soat-cong-no/${r.id}`) })}
          locale={{ emptyText: 'Chưa có kỳ đối soát nào' }}
          columns={[
            { title: 'Mã', dataIndex: 'code', width: 165, render: (v) => <b>{v}</b> },
            { title: 'Nhà cung cấp', dataIndex: ['supplier', 'name'] },
            { title: 'Kỳ đối soát', width: 200, render: (_, r) => `${fmtDate(r.periodFrom)} – ${fmtDate(r.periodTo)}` },
            { title: 'Theo file NCC', dataIndex: 'fileTotal', width: 140, align: 'right', render: fmtMoney },
            { title: 'Theo hệ thống', dataIndex: 'systemTotal', width: 140, align: 'right', render: fmtMoney },
            { title: 'Khớp / Lệch', width: 120, render: (_, r) => <Space size={4}><b style={{ color: '#008300' }}>{r.matchedCount}</b>/<b style={{ color: r.mismatchCount ? '#c4320a' : undefined }}>{r.mismatchCount}</b></Space> },
            { title: 'Trạng thái', dataIndex: 'status', width: 190, render: (v) => <ReconStatusTag status={v} /> },
            { title: 'Người tạo', width: 170, render: (_, r) => <>{r.createdBy?.fullName}<div className="muted small">{fmtDateTime(r.createdAt)}</div></> },
          ]} />
      </Card>
      <Modal open={open} title="Tạo kỳ đối soát công nợ" onOk={create} onCancel={() => setOpen(false)} okText="Đối chiếu" cancelText="Hủy" confirmLoading={saving} width={620} destroyOnHidden>
        <Alert type="info" showIcon style={{ marginBottom: 16 }}
          message="File hóa đơn (Excel/CSV) cần có cột: Số hóa đơn, Số tiền. Cột Ngày hóa đơn và Đã thanh toán là tùy chọn." />
        <Form form={form} layout="vertical">
          <Form.Item name="supplierId" label="Nhà cung cấp" rules={[{ required: true, message: 'Chọn nhà cung cấp' }]}>
            <Select showSearch optionFilterProp="label" options={(suppliers ?? []).map((s) => ({ value: s.id, label: s.name }))} />
          </Form.Item>
          <Form.Item name="period" label="Kỳ đối soát" rules={[{ required: true, message: 'Chọn kỳ đối soát' }]}>
            <DatePicker.RangePicker format="DD/MM/YYYY" style={{ width: '100%' }} placeholder={['Từ ngày', 'Đến ngày']} />
          </Form.Item>
          <Form.Item label="File hóa đơn của nhà cung cấp" required>
            <UploadField value={invoiceFile} onChange={setInvoiceFile} accept=".xlsx,.xls,.csv" hint="Bảng kê hóa đơn trong kỳ (.xlsx, .xls, .csv)" />
          </Form.Item>
          <Form.Item label="Minh chứng thanh toán (không bắt buộc)">
            <UploadField value={evidenceFile} onChange={setEvidenceFile} accept=".pdf,.jpg,.jpeg,.png,.webp" dragger={false} label="Chọn minh chứng" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
