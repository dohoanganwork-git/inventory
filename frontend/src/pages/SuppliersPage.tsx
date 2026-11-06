import { Button, Card, Col, DatePicker, Descriptions, Divider, Drawer, Form, Input, Modal, Row, Segmented, Select, Space, Table, Tabs, Tooltip } from 'antd';
import { EditOutlined, PlusOutlined, StopOutlined, CheckOutlined, SearchOutlined, PhoneOutlined, MailOutlined, EyeOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { ActiveTag, ContractTag, DebtStatusTag, DocStatusTag, StockStatusTag } from '../components/StatusTags';
import FileLink from '../components/FileLink';
import UploadField from '../components/UploadField';
import { useFetch } from '../utils/useFetch';
import { api, multipart, showError } from '../api/client';
import type { Item, Receipt, Supplier } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtMoney, normalize, toIso } from '../utils/format';
import { PAYMENT_METHODS } from '../utils/labels';
import { useAuth } from '../auth/AuthContext';

function SupplierDrawer({ id, onClose, onEdit }: { id: string | null; onClose: () => void; onEdit?: (s: Supplier) => void }) {
  const nav = useNavigate();
  const { canSeeValues } = useAuth();
  const { data: s } = useFetch<Supplier>(id ? `/suppliers/${id}` : null);
  const { data: items } = useFetch<Item[]>(id ? '/items' : null, { supplierId: id });
  const { data: receipts } = useFetch<Receipt[]>(id ? '/receipts' : null, { supplierId: id });
  const shown = s && s.id === id ? s : null;
  return (
    <Drawer open={!!id} onClose={onClose} width={Math.min(820, window.innerWidth)} title={shown?.name ?? 'Nhà cung cấp'}
      extra={shown && onEdit && <Button icon={<EditOutlined />} onClick={() => onEdit(shown)}>Chỉnh sửa</Button>}>
      {shown && (
        <Tabs items={[
          {
            key: 'info', label: 'Thông tin liên hệ', children: (
              <Descriptions bordered size="small" column={1} className="kv-grid">
                <Descriptions.Item label="Mã nhà cung cấp"><b>{shown.code}</b></Descriptions.Item>
                <Descriptions.Item label="Người liên hệ">{shown.contactName ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="Số điện thoại">{shown.phone ? <a href={`tel:${shown.phone}`}><PhoneOutlined /> {shown.phone}</a> : '—'}</Descriptions.Item>
                <Descriptions.Item label="Email">{shown.email ? <a href={`mailto:${shown.email}`}><MailOutlined /> {shown.email}</a> : '—'}</Descriptions.Item>
                <Descriptions.Item label="Địa chỉ">{shown.address ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="Mã số thuế">{shown.taxCode ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="Phương thức thanh toán">{shown.paymentMethod ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="Tài khoản ngân hàng">{shown.bankAccount ?? '—'}</Descriptions.Item>
                {canSeeValues && <Descriptions.Item label="Công nợ hiện tại"><b>{fmtMoney(shown.openDebt)}</b></Descriptions.Item>}
                <Descriptions.Item label="Trạng thái"><ActiveTag active={shown.active} on="Đang hợp tác" off="Ngừng hợp tác" /></Descriptions.Item>
              </Descriptions>
            ),
          },
          {
            key: 'contract', label: 'Hợp đồng', children: (
              <Descriptions bordered size="small" column={1} className="kv-grid">
                <Descriptions.Item label="Số hợp đồng">{shown.contractNo ?? '—'}</Descriptions.Item>
                <Descriptions.Item label="Hiệu lực">{shown.contractStart || shown.contractEnd ? `${fmtDate(shown.contractStart)} → ${fmtDate(shown.contractEnd)}` : '—'}</Descriptions.Item>
                <Descriptions.Item label="Tình trạng"><ContractTag status={shown.contractStatus} /></Descriptions.Item>
                <Descriptions.Item label="Điều khoản / ghi chú"><div style={{ whiteSpace: 'pre-line' }}>{shown.contractNote ?? '—'}</div></Descriptions.Item>
                <Descriptions.Item label="Tệp hợp đồng"><FileLink file={shown.contractFile} /></Descriptions.Item>
              </Descriptions>
            ),
          },
          {
            key: 'items', label: `Mặt hàng (${items?.length ?? 0})`, children: (
              <Table<Item> size="small" rowKey="id" dataSource={items ?? []} pagination={false} scroll={{ x: 500 }}
                columns={[
                  { title: 'Mã', dataIndex: 'code' }, { title: 'Tên', dataIndex: 'name' },
                  { title: 'Tồn', dataIndex: 'currentStock', align: 'right' },
                  { title: 'Trạng thái', render: (_, r) => <StockStatusTag status={r.stockStatus} stock={r.currentStock} /> },
                ]} />
            ),
          },
          {
            key: 'receipts', label: 'Phiếu nhập gần đây', children: (
              <Table<Receipt> size="small" rowKey="id" dataSource={(receipts ?? []).slice(0, 20)} pagination={false} scroll={{ x: 560 }}
                rowClassName="row-clickable" onRow={(r) => ({ onClick: () => nav(`/phieu-nhap/${r.id}`) })}
                columns={[
                  { title: 'Mã phiếu', dataIndex: 'code' }, { title: 'Ngày', dataIndex: 'receiptDate', render: fmtDate },
                  { title: 'Số HĐ', dataIndex: 'invoiceNo' }, { title: 'Tổng tiền', dataIndex: 'totalAmount', align: 'right', render: fmtMoney },
                  { title: 'Trạng thái', render: (_, r) => (r.status === 'CANCELLED' ? <DocStatusTag status={r.status} /> : <DebtStatusTag status={r.debtStatus} overdue={r.overdue} />) },
                ]} />
            ),
          },
        ]} />
      )}
    </Drawer>
  );
}

export default function SuppliersPage() {
  const { hasRole, canSeeValues } = useAuth();
  const isManager = hasRole('MANAGER');
  const { data, loading, reload } = useFetch<Supplier[]>('/suppliers');
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<'active' | 'inactive' | 'all'>('active');
  const [contract, setContract] = useState<string | undefined>();
  const [viewing, setViewing] = useState<string | null>(null);
  const [editing, setEditing] = useState<Supplier | 'new' | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm();

  const rows = useMemo(() => (data ?? []).filter((s) => (status === 'all' || s.active === (status === 'active'))
    && (!contract || s.contractStatus === contract)
    && (!q || normalize(`${s.code} ${s.name} ${s.contactName ?? ''} ${s.phone ?? ''} ${s.contractNo ?? ''}`).includes(normalize(q)))), [data, q, status, contract]);

  const open = (s: Supplier | 'new') => {
    setViewing(null);
    setEditing(s);
    setFile(null);
    form.resetFields();
    if (s !== 'new') {
      form.setFieldsValue({ ...s, contractPeriod: s.contractStart || s.contractEnd ? [s.contractStart ? dayjs(s.contractStart) : null, s.contractEnd ? dayjs(s.contractEnd) : null] : undefined });
    } else form.setFieldsValue({ paymentMethod: 'Chuyển khoản' });
  };

  const save = async () => {
    const v = await form.validateFields();
    const { contractPeriod, ...rest } = v;
    const body = { ...rest, contractStart: toIso(contractPeriod?.[0]), contractEnd: toIso(contractPeriod?.[1]) };
    setSaving(true);
    try {
      const fd = multipart(body, { contractFile: file });
      if (editing === 'new') await api.post('/suppliers', fd);
      else if (editing) await api.put(`/suppliers/${editing.id}`, fd);
      ui.message.success(editing === 'new' ? 'Thêm nhà cung cấp thành công' : 'Cập nhật thành công');
      setEditing(null);
      reload();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  const toggle = (s: Supplier) => ui.modal.confirm({
    title: s.active ? `Ngừng hợp tác với "${s.name}"?` : `Hợp tác lại với "${s.name}"?`,
    content: s.active ? 'Nhà cung cấp sẽ không xuất hiện khi lập phiếu nhập mới.' : undefined,
    okText: 'Xác nhận', cancelText: 'Đóng',
    onOk: async () => { try { await api.patch(`/suppliers/${s.id}/status`, { active: !s.active }); reload(); } catch (e) { showError(e); } },
  });

  return (
    <>
      <PageHeader title="Nhà cung cấp & hợp đồng" subtitle="Lưu tập trung thông tin liên hệ, phương thức thanh toán và hợp đồng cung cấp để tra cứu nhanh"
        extra={isManager && <Button type="primary" icon={<PlusOutlined />} onClick={() => open('new')}>Thêm nhà cung cấp</Button>} />
      <Card className="section-card">
        <div className="filter-bar">
          <Input allowClear prefix={<SearchOutlined />} placeholder="Tìm theo tên, người liên hệ, SĐT, số hợp đồng..." value={q} onChange={(e) => setQ(e.target.value)} style={{ maxWidth: 360 }} />
          <Select allowClear placeholder="Tình trạng hợp đồng" value={contract} onChange={setContract}
            options={[{ value: 'ACTIVE', label: 'Còn hiệu lực' }, { value: 'EXPIRING', label: 'Sắp hết hạn (≤ 30 ngày)' }, { value: 'EXPIRED', label: 'Hết hạn' }, { value: 'NONE', label: 'Chưa có hợp đồng' }]} style={{ minWidth: 220 }} />
          <Segmented value={status} onChange={(v) => setStatus(v as typeof status)}
            options={[{ label: 'Đang hợp tác', value: 'active' }, { label: 'Ngừng', value: 'inactive' }, { label: 'Tất cả', value: 'all' }]} />
        </div>
        <Table<Supplier> rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 1080 }} pagination={{ pageSize: 20, hideOnSinglePage: true }}
          rowClassName={(r) => `row-clickable ${r.active ? '' : 'row-muted'}`} onRow={(r) => ({ onClick: () => setViewing(r.id) })}
          locale={{ emptyText: 'Không tìm thấy nhà cung cấp phù hợp' }}
          columns={[
            { title: 'Mã', dataIndex: 'code', width: 110, render: (v) => <b>{v}</b> },
            { title: 'Nhà cung cấp', dataIndex: 'name', render: (v, r) => <>{v}<div className="muted small">{r.itemCount} mặt hàng · {r.paymentMethod ?? '—'}</div></> },
            { title: 'Người liên hệ', dataIndex: 'contactName', render: (v, r) => <>{v ?? '—'}<div className="small">{r.phone && <a href={`tel:${r.phone}`} onClick={(e) => e.stopPropagation()}><PhoneOutlined /> {r.phone}</a>}</div></> },
            { title: 'Hợp đồng', dataIndex: 'contractNo', render: (v, r) => <>{v ?? '—'}{r.contractEnd && <div className="muted small">đến {fmtDate(r.contractEnd)}</div>}</> },
            { title: 'Tình trạng HĐ', dataIndex: 'contractStatus', width: 150, render: (v) => <ContractTag status={v} /> },
            ...(canSeeValues ? [{ title: 'Công nợ', dataIndex: 'openDebt', align: 'right' as const, width: 130, render: (v: number) => (v > 0 ? <b>{fmtMoney(v)}</b> : <span className="muted">0 đ</span>),
              sorter: (a: Supplier, b: Supplier) => (a.openDebt ?? 0) - (b.openDebt ?? 0) }] : []),
            { title: 'Trạng thái', dataIndex: 'active', width: 140, render: (v) => <ActiveTag active={v} on="Đang hợp tác" off="Ngừng hợp tác" /> },
            {
              title: 'Thao tác', fixed: 'right', width: isManager ? 140 : 70, render: (_, r) => (
                <Space onClick={(e) => e.stopPropagation()}>
                  <Tooltip title="Xem chi tiết"><Button icon={<EyeOutlined />} onClick={() => setViewing(r.id)} /></Tooltip>
                  {isManager && <Tooltip title="Chỉnh sửa"><Button icon={<EditOutlined />} onClick={() => open(r)} /></Tooltip>}
                  {isManager && <Tooltip title={r.active ? 'Ngừng hợp tác' : 'Hợp tác lại'}><Button icon={r.active ? <StopOutlined /> : <CheckOutlined />} onClick={() => toggle(r)} /></Tooltip>}
                </Space>
              ),
            },
          ]} />
      </Card>
      <SupplierDrawer id={viewing} onClose={() => setViewing(null)} onEdit={isManager ? open : undefined} />
      <Modal open={!!editing} title={editing === 'new' ? 'Thêm nhà cung cấp' : 'Chỉnh sửa nhà cung cấp'} onOk={save} onCancel={() => setEditing(null)}
        okText="Lưu" cancelText="Hủy" confirmLoading={saving} width={760} destroyOnHidden>
        <Form form={form} layout="vertical" preserve={false}>
          <Divider orientation="left" plain style={{ marginTop: 0 }}>Thông tin liên hệ</Divider>
          <Row gutter={12}>
            <Col xs={24} sm={8}><Form.Item name="code" label="Mã NCC" rules={[{ required: true, message: 'Nhập mã' }]}><Input style={{ textTransform: 'uppercase' }} /></Form.Item></Col>
            <Col xs={24} sm={16}><Form.Item name="name" label="Tên nhà cung cấp" rules={[{ required: true, message: 'Nhập tên nhà cung cấp' }]}><Input /></Form.Item></Col>
            <Col xs={24} sm={8}><Form.Item name="contactName" label="Người liên hệ"><Input /></Form.Item></Col>
            <Col xs={24} sm={8}><Form.Item name="phone" label="Số điện thoại" rules={[{ pattern: /^(\+84|0)[0-9]{9,10}$/, message: 'Không đúng định dạng' }]}><Input /></Form.Item></Col>
            <Col xs={24} sm={8}><Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Không đúng định dạng' }]}><Input /></Form.Item></Col>
            <Col xs={24} sm={16}><Form.Item name="address" label="Địa chỉ"><Input /></Form.Item></Col>
            <Col xs={24} sm={8}><Form.Item name="taxCode" label="Mã số thuế"><Input /></Form.Item></Col>
            <Col xs={24} sm={8}><Form.Item name="paymentMethod" label="Phương thức thanh toán"><Select options={PAYMENT_METHODS.map((m) => ({ value: m, label: m }))} /></Form.Item></Col>
            <Col xs={24} sm={16}><Form.Item name="bankAccount" label="Tài khoản ngân hàng"><Input placeholder="Ngân hàng – số tài khoản – chủ tài khoản" /></Form.Item></Col>
          </Row>
          <Divider orientation="left" plain>Hợp đồng cung cấp</Divider>
          <Row gutter={12}>
            <Col xs={24} sm={10}><Form.Item name="contractNo" label="Số hợp đồng"><Input /></Form.Item></Col>
            <Col xs={24} sm={14}><Form.Item name="contractPeriod" label="Thời hạn hợp đồng"><DatePicker.RangePicker format="DD/MM/YYYY" style={{ width: '100%' }} placeholder={['Từ ngày', 'Đến ngày']} allowEmpty={[true, true]} /></Form.Item></Col>
            <Col span={24}><Form.Item name="contractNote" label="Điều khoản / ghi chú"><Input.TextArea rows={3} /></Form.Item></Col>
            <Col span={24}>
              <Form.Item label="Tệp hợp đồng (PDF, ảnh, Word – tối đa 10MB)"
                extra={editing && editing !== 'new' && editing.contractFile ? <>Tệp hiện tại: <FileLink file={editing.contractFile} /> (chọn tệp mới để thay thế)</> : undefined}>
                <UploadField value={file} onChange={setFile} accept=".pdf,.jpg,.jpeg,.png,.webp,.doc,.docx" dragger={false} label="Chọn tệp hợp đồng" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </>
  );
}
