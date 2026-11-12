import { Alert, Button, Card, Col, DatePicker, Form, Modal, Progress, Row, Space, Table } from 'antd';
import { PlusOutlined, DownloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { StocktakeStatusTag } from '../components/StatusTags';
import UploadField from '../components/UploadField';
import { useFetch } from '../utils/useFetch';
import { api, download, showError } from '../api/client';
import type { Stocktake } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtDateTime, toIso } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

export default function StocktakesPage() {
  const { hasRole } = useAuth();
  const nav = useNavigate();
  const { data, loading } = useFetch<Stocktake[]>('/stocktakes');
  const [open, setOpen] = useState(false);
  const [countFile, setCountFile] = useState<File | null>(null);
  const [damageFile, setDamageFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();
  const openOne = (data ?? []).find((s) => s.status !== 'COMPLETED');
  const canUpload = hasRole('STAFF', 'MANAGER');

  const create = async () => {
    const v = await form.validateFields().catch(() => null);
    if (!v) return;
    if (!countFile) { ui.message.warning('Vui lòng chọn file tồn thực tế'); return; }
    const fd = new FormData();
    fd.append('countFile', countFile);
    if (damageFile) fd.append('damageFile', damageFile);
    setSaving(true);
    try {
      const { data: s } = await api.post<Stocktake>('/stocktakes', fd, { params: { stocktakeDate: toIso(v.date) } });
      ui.message.success(s.status === 'COMPLETED' ? 'Kết quả khớp toàn bộ – đợt kiểm kê đã hoàn tất' : `Đã gửi file. Có ${s.pendingLines} mặt hàng chờ Quản lý xem xét.`);
      nav(`/kiem-ke/${s.id}`);
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  return (
    <>
      <PageHeader title="Kiểm kê & đối soát tồn kho"
        subtitle="Nhân viên tải file kiểm kê định kỳ; hệ thống so sánh với tồn sổ sách và đánh dấu chênh lệch. Tồn kho chỉ thay đổi khi Quản lý xác nhận điều chỉnh."
        extra={canUpload && <>
          <Button icon={<DownloadOutlined />} onClick={() => download('/templates/stocktake')}>Mẫu kiểm kê</Button>
          <Button icon={<DownloadOutlined />} onClick={() => download('/templates/damage')}>Mẫu hỏng/hủy</Button>
          <Button type="primary" icon={<PlusOutlined />} disabled={!!openOne}
            onClick={() => { form.setFieldsValue({ date: dayjs() }); setCountFile(null); setDamageFile(null); setOpen(true); }}>Tải lên file kiểm kê</Button>
        </>} />
      {openOne && (
        <Alert type={openOne.status === 'RECOUNT_REQUESTED' ? 'error' : 'warning'} showIcon style={{ marginBottom: 16 }}
          message={openOne.status === 'RECOUNT_REQUESTED'
            ? `Đợt ${openOne.code} đang chờ kiểm kê lại ${openOne.recountLines} mặt hàng`
            : `Đợt ${openOne.code} đang chờ Quản lý xem xét (${openOne.pendingLines} mặt hàng chênh lệch)`}
          description="Mỗi thời điểm chỉ có một đợt kiểm kê đang mở. Hãy hoàn tất đợt này trước khi tạo đợt mới."
          action={<Button size="small" type="primary" onClick={() => nav(`/kiem-ke/${openOne.id}`)}>Mở đợt kiểm kê</Button>} />
      )}
      <Card className="section-card">
        <Table<Stocktake> rowKey="id" loading={loading} dataSource={data ?? []} scroll={{ x: 960 }}
          rowClassName="row-clickable" onRow={(r) => ({ onClick: () => nav(`/kiem-ke/${r.id}`) })}
          locale={{ emptyText: 'Chưa có đợt kiểm kê nào' }}
          columns={[
            { title: 'Mã đợt', dataIndex: 'code', width: 165, render: (v) => <b>{v}</b> },
            { title: 'Ngày kiểm kê', dataIndex: 'stocktakeDate', width: 120, render: fmtDate },
            { title: 'Số MH kiểm', dataIndex: 'totalLines', width: 110, align: 'right' },
            { title: 'Lệch / Hỏng', width: 120, render: (_, r) => `${r.varianceLines} / ${r.damagedQty}` },
            { title: 'Tỉ lệ chính xác', dataIndex: 'accuracy', width: 180, render: (v: number) => (
              <Space><Progress percent={v} size="small" showInfo={false} strokeColor={v >= 95 ? '#1baf7a' : v >= 85 ? '#eda100' : '#e34948'} style={{ width: 90, margin: 0 }} /><b>{v}%</b></Space>) },
            { title: 'Lần kiểm', dataIndex: 'round', width: 90, align: 'right' },
            { title: 'Trạng thái', dataIndex: 'status', width: 200, render: (v) => <StocktakeStatusTag status={v} /> },
            { title: 'Người gửi', width: 170, render: (_, r) => <>{r.createdBy?.fullName}<div className="muted small">{fmtDateTime(r.createdAt)}</div></> },
            { title: 'Người duyệt', dataIndex: ['reviewedBy', 'fullName'], width: 150, render: (v) => v ?? '—' },
          ]} />
      </Card>
      <Modal open={open} title="Tải lên file kiểm kê" onOk={create} onCancel={() => setOpen(false)} okText="Gửi & đối chiếu" cancelText="Hủy" confirmLoading={saving} width={640} destroyOnHidden>
        <Alert type="info" showIcon style={{ marginBottom: 16 }}
          message="Doanh số POS phải được nhập đến ngày trước ngày kiểm kê. File kiểm kê chỉ dùng để đối chiếu – tồn kho không thay đổi cho đến khi Quản lý xác nhận." />
        <Form form={form} layout="vertical">
          <Form.Item name="date" label="Ngày kiểm kê" rules={[{ required: true, message: 'Chọn ngày kiểm kê' }]}>
            <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
          </Form.Item>
          <Row gutter={12}>
            <Col xs={24} sm={12}><Form.Item label="File tồn thực tế" required>
              <UploadField value={countFile} onChange={setCountFile} accept=".xlsx,.xls,.csv" hint="Mã hàng, Số lượng thực tế" /></Form.Item></Col>
            <Col xs={24} sm={12}><Form.Item label="File hỏng/hủy (nếu có)">
              <UploadField value={damageFile} onChange={setDamageFile} accept=".xlsx,.xls,.csv" hint="Mã hàng, Số lượng hỏng/hủy, Lý do" /></Form.Item></Col>
          </Row>
        </Form>
      </Modal>
    </>
  );
}
