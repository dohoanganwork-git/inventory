import { Alert, Button, Card, Col, DatePicker, Form, Input, InputNumber, Row, Select, Skeleton, Space, Table, Typography } from 'antd';
import type { SelectProps } from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import UploadField from '../components/UploadField';
import { askReason } from '../components/ReasonModal';
import { useFetch } from '../utils/useFetch';
import { api, multipart, showError } from '../api/client';
import type { Item, Receipt, Supplier } from '../api/types';
import { ui } from '../utils/globals';
import { fmtMoney, toIso } from '../utils/format';

interface Line { key: number; itemId?: string; quantity?: number; unitPrice?: number }
let keySeq = 1;

const moneyInput = {
  formatter: (v?: string | number) => `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.'),
  parser: (v?: string) => Number((v ?? '').replace(/\./g, '')) as unknown as 0,
};

export default function ReceiptFormPage() {
  const { id } = useParams();
  const isEdit = !!id;
  const nav = useNavigate();
  const [params] = useSearchParams();
  const [form] = Form.useForm();
  const { data: suppliers } = useFetch<Supplier[]>('/suppliers', { active: true });
  const { data: items } = useFetch<Item[]>('/items');
  const { data: existing, loading: loadingExisting } = useFetch<Receipt>(isEdit ? `/receipts/${id}` : null);
  const [lines, setLines] = useState<Line[]>([{ key: keySeq++ }]);
  const [invoiceFile, setInvoiceFile] = useState<File | null>(null);
  const [duplicate, setDuplicate] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const supplierId = Form.useWatch('supplierId', form);

  // Điền sẵn từ cảnh báo tồn thấp (?supplierId=&itemId=)
  useEffect(() => {
    if (isEdit) return;
    const sid = params.get('supplierId');
    const iid = params.get('itemId');
    form.setFieldsValue({ receiptDate: dayjs(), dueDate: dayjs().add(30, 'day'), supplierId: sid || undefined });
    if (iid && items) {
      const it = items.find((x) => x.id === iid);
      if (it) setLines([{ key: keySeq++, itemId: iid, quantity: Math.max(1, (it.maxStock ?? it.minStock * 3) - it.currentStock), unitPrice: it.lastCost ?? undefined }]);
    }
  }, [isEdit, params, items, form]);

  useEffect(() => {
    if (!existing) return;
    form.setFieldsValue({
      supplierId: existing.supplier.id, invoiceNo: existing.invoiceNo, receiptDate: dayjs(existing.receiptDate),
      dueDate: existing.dueDate ? dayjs(existing.dueDate) : undefined, note: existing.note,
    });
    setLines((existing.lines ?? []).map((l) => ({ key: keySeq++, itemId: l.item.id, quantity: l.quantity, unitPrice: l.unitPrice })));
  }, [existing, form]);

  const itemMap = useMemo(() => new Map((items ?? []).map((i) => [i.id, i])), [items]);
  const usedIds = new Set(lines.map((l) => l.itemId).filter(Boolean));
  const itemOptions = useMemo<SelectProps['options']>(() => {
    const list = (items ?? []).filter((i) => i.active || existing?.lines?.some((l) => l.item.id === i.id));
    const mine = list.filter((i) => supplierId && i.supplier?.id === supplierId);
    const others = list.filter((i) => !(supplierId && i.supplier?.id === supplierId));
    const opt = (i: Item) => ({ value: i.id, label: `${i.code} – ${i.name}`, disabled: false });
    return supplierId
      ? [{ label: 'Mặt hàng của nhà cung cấp này', options: mine.map(opt) }, { label: 'Mặt hàng khác', options: others.map(opt) }]
      : list.map(opt);
  }, [items, supplierId, existing]);

  const total = lines.reduce((s, l) => s + (l.quantity ?? 0) * (l.unitPrice ?? 0), 0);
  const update = (key: number, patch: Partial<Line>) => setLines((ls) => ls.map((l) => (l.key === key ? { ...l, ...patch } : l)));

  const checkDuplicate = async () => {
    const sid = form.getFieldValue('supplierId');
    const inv = form.getFieldValue('invoiceNo');
    if (!sid || !inv) { setDuplicate(null); return; }
    try {
      const { data } = await api.get<{ duplicate: boolean; receiptCode?: string }>('/receipts/check-invoice', { params: { supplierId: sid, invoiceNo: inv, excludeId: id } });
      setDuplicate(data.duplicate ? data.receiptCode ?? '' : null);
    } catch { setDuplicate(null); }
  };

  const save = async () => {
    const v = await form.validateFields();
    const valid = lines.filter((l) => l.itemId);
    if (valid.length === 0) { ui.message.warning('Phiếu nhập phải có ít nhất một mặt hàng'); return; }
    const bad = valid.find((l) => !l.quantity || l.quantity <= 0 || !l.unitPrice || l.unitPrice <= 0);
    if (bad) { ui.message.warning(`Số lượng và đơn giá của "${itemMap.get(bad.itemId!)?.name}" phải lớn hơn 0`); return; }
    if (duplicate) { ui.message.error(`Số hóa đơn đã được nhập ở phiếu ${duplicate}. Vui lòng kiểm tra lại.`); return; }
    const body = {
      supplierId: v.supplierId, invoiceNo: v.invoiceNo, receiptDate: toIso(v.receiptDate), dueDate: toIso(v.dueDate), note: v.note,
      lines: valid.map((l) => ({ itemId: l.itemId, quantity: l.quantity, unitPrice: l.unitPrice })), reason: undefined as string | undefined,
    };
    if (isEdit) {
      const reason = await askReason({ title: 'Lý do chỉnh sửa phiếu nhập', content: 'Tồn kho và công nợ sẽ được tính lại theo nội dung mới.', okText: 'Lưu thay đổi' });
      if (!reason) return;
      body.reason = reason;
    }
    setSaving(true);
    try {
      const { data } = isEdit
        ? await api.put<Receipt>(`/receipts/${id}`, body)
        : await api.post<Receipt>('/receipts', multipart(body, { invoiceFile }));
      ui.message.success(isEdit ? 'Đã cập nhật phiếu nhập' : `Lưu phiếu ${data.code} thành công – tồn kho đã được cập nhật`);
      nav(`/phieu-nhap/${data.id}`, { replace: true });
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  if (isEdit && (loadingExisting || !existing)) return <Skeleton active />;

  return (
    <>
      <PageHeader back title={isEdit ? `Sửa phiếu nhập ${existing?.code}` : 'Tạo phiếu nhập hàng'}
        subtitle={isEdit ? 'Chỉ Quản lý được sửa phiếu. Mọi thay đổi đều được lưu lịch sử kèm lý do.' : 'Lập phiếu khi nhận sản phẩm từ nhà cung cấp. Hệ thống tự tăng tồn kho và tạo công nợ chưa thanh toán.'} />
      <Form form={form} layout="vertical">
        <Card className="section-card" title="Thông tin phiếu nhập">
          <Row gutter={16}>
            <Col xs={24} md={12} lg={8}>
              <Form.Item name="supplierId" label="Nhà cung cấp" rules={[{ required: true, message: 'Vui lòng chọn nhà cung cấp' }]}>
                <Select showSearch optionFilterProp="label" placeholder="Chọn nhà cung cấp" onChange={() => setTimeout(checkDuplicate)}
                  options={(suppliers ?? []).filter((s) => s.active || s.id === existing?.supplier.id).map((s) => ({ value: s.id, label: s.name }))} />
              </Form.Item>
            </Col>
            <Col xs={24} md={12} lg={6}>
              <Form.Item name="invoiceNo" label="Số hóa đơn" rules={[{ required: true, message: 'Vui lòng nhập số hóa đơn' }]}>
                <Input placeholder="Số hóa đơn trên chứng từ NCC" onBlur={checkDuplicate} />
              </Form.Item>
            </Col>
            <Col xs={12} lg={5}>
              <Form.Item name="receiptDate" label="Ngày nhập" rules={[{ required: true, message: 'Chọn ngày nhập' }]}>
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              </Form.Item>
            </Col>
            <Col xs={12} lg={5}>
              <Form.Item name="dueDate" label="Hạn thanh toán">
                <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col xs={24} lg={isEdit ? 24 : 14}>
              <Form.Item name="note" label="Ghi chú"><Input.TextArea rows={2} maxLength={500} placeholder="Ghi chú khi nhận hàng (nếu có)" /></Form.Item>
            </Col>
            {!isEdit && (
              <Col xs={24} lg={10}>
                <Form.Item label="Ảnh / tệp hóa đơn (không bắt buộc)">
                  <UploadField value={invoiceFile} onChange={setInvoiceFile} accept=".pdf,.jpg,.jpeg,.png,.webp" dragger={false} label="Chọn ảnh hóa đơn" />
                </Form.Item>
              </Col>
            )}
          </Row>
          {duplicate !== null && (
            <Alert type="error" showIcon message={`Số hóa đơn này của nhà cung cấp đã được nhập ở phiếu ${duplicate}`}
              description="Vui lòng kiểm tra lại hóa đơn, chỉnh sửa số hóa đơn hoặc hủy thao tác để tránh nhập trùng." />
          )}
        </Card>

        <Card className="section-card" title="Danh sách mặt hàng nhận"
          extra={<Button icon={<PlusOutlined />} onClick={() => setLines((ls) => [...ls, { key: keySeq++ }])}>Thêm dòng</Button>}>
          <Table<Line> rowKey="key" dataSource={lines} pagination={false} scroll={{ x: 820 }}
            columns={[
              { title: '#', width: 44, render: (_, __, i) => i + 1 },
              {
                title: 'Mặt hàng', render: (_, l) => (
                  <Select showSearch optionFilterProp="label" placeholder="Chọn mặt hàng" style={{ width: '100%', minWidth: 260 }} value={l.itemId}
                    onChange={(v) => {
                      if (usedIds.has(v) && v !== l.itemId) { ui.message.warning('Mặt hàng đã có trong phiếu – hãy cộng dồn số lượng'); return; }
                      const it = itemMap.get(v);
                      update(l.key, { itemId: v, unitPrice: l.unitPrice ?? it?.lastCost ?? undefined });
                    }}
                    options={itemOptions} />
                ),
              },
              { title: 'ĐVT', width: 70, render: (_, l) => itemMap.get(l.itemId ?? '')?.unit ?? '—' },
              { title: 'Tồn hiện tại', width: 100, align: 'right', render: (_, l) => itemMap.get(l.itemId ?? '')?.currentStock ?? '—' },
              {
                title: 'Số lượng', width: 120, render: (_, l) => (
                  <InputNumber min={1} value={l.quantity} onChange={(v) => update(l.key, { quantity: v ?? undefined })} style={{ width: '100%' }} placeholder="0" />
                ),
              },
              {
                title: 'Đơn giá (đ)', width: 150, render: (_, l) => (
                  <InputNumber min={1} step={1000} value={l.unitPrice} onChange={(v) => update(l.key, { unitPrice: v ?? undefined })} style={{ width: '100%' }} placeholder="0" {...moneyInput} />
                ),
              },
              { title: 'Thành tiền', width: 140, align: 'right', render: (_, l) => <b>{fmtMoney((l.quantity ?? 0) * (l.unitPrice ?? 0))}</b> },
              {
                title: '', width: 50, render: (_, l) => (
                  <Button danger type="text" icon={<DeleteOutlined />} aria-label="Xóa dòng" disabled={lines.length === 1}
                    onClick={() => setLines((ls) => ls.filter((x) => x.key !== l.key))} />
                ),
              },
            ]} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 16, flexWrap: 'wrap', gap: 12 }}>
            <Typography.Text type="secondary">{lines.filter((l) => l.itemId).length} mặt hàng · {lines.reduce((s, l) => s + (l.quantity ?? 0), 0)} đơn vị</Typography.Text>
            <Space size="large" wrap>
              <span style={{ fontSize: 16 }}>Tổng tiền: <b style={{ fontSize: 20 }}>{fmtMoney(total)}</b></span>
              <Button onClick={() => nav(-1)}>Hủy</Button>
              <Button type="primary" size="large" icon={<SaveOutlined />} loading={saving} onClick={save}>{isEdit ? 'Lưu thay đổi' : 'Lưu phiếu nhập'}</Button>
            </Space>
          </div>
        </Card>
      </Form>
    </>
  );
}
