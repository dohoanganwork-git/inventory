import { Alert, Col, DatePicker, Form, Input, InputNumber, Modal, Row, Select, Table, Typography } from 'antd';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import type { Payment, Receipt } from '../api/types';
import { api, multipart, showError } from '../api/client';
import { ui } from '../utils/globals';
import { fmtDate, fmtMoney, toIso } from '../utils/format';
import { PAYMENT_METHODS } from '../utils/labels';
import UploadField from './UploadField';

const moneyInput = {
  formatter: (v?: string | number) => `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.'),
  parser: (v?: string) => Number((v ?? '').replace(/\./g, '')) as unknown as 0,
};

/** UC_15 – Thanh toán một hoặc nhiều phiếu nhập của cùng nhà cung cấp, kèm minh chứng. */
export function PaymentModal({ receipts, open, onClose, onDone }: { receipts: Receipt[]; open: boolean; onClose: () => void; onDone: () => void }) {
  const [form] = Form.useForm();
  const [amounts, setAmounts] = useState<Record<string, number | null>>({});
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ paidAt: dayjs(), method: receipts[0]?.supplier ? 'Chuyển khoản' : undefined });
    setAmounts(Object.fromEntries(receipts.map((r) => [r.id, r.remainingAmount ?? 0])));
    setFile(null);
  }, [open, receipts, form]);

  const total = Object.values(amounts).reduce<number>((s, v) => s + (v ?? 0), 0);

  const submit = async () => {
    const v = await form.validateFields().catch(() => null);
    if (!v) return;
    for (const r of receipts) {
      const a = amounts[r.id] ?? 0;
      if (a <= 0) { ui.message.warning(`Số tiền thanh toán cho phiếu ${r.code} phải lớn hơn 0`); return; }
      if (a > (r.remainingAmount ?? 0)) { ui.message.warning(`Số tiền cho phiếu ${r.code} lớn hơn số còn nợ`); return; }
    }
    if (!file) { ui.message.warning('Vui lòng đính kèm minh chứng thanh toán'); return; }
    setSaving(true);
    try {
      await api.post('/payments', multipart({
        allocations: receipts.map((r) => ({ receiptId: r.id, amount: amounts[r.id] })),
        paidAt: toIso(v.paidAt), method: v.method, note: v.note,
      }, { evidence: file }));
      ui.message.success(`Đã ghi nhận thanh toán ${fmtMoney(total)}`);
      onDone();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  return (
    <Modal open={open} onCancel={onClose} onOk={submit} confirmLoading={saving} width={760} okText="Lưu thanh toán" cancelText="Hủy"
      title={`Thanh toán công nợ – ${receipts[0]?.supplier.name ?? ''}`} destroyOnHidden>
      <Table<Receipt> size="small" rowKey="id" dataSource={receipts} pagination={false} scroll={{ x: 560 }} style={{ marginBottom: 16 }}
        columns={[
          { title: 'Phiếu nhập', dataIndex: 'code', render: (v, r) => <>{v}<div className="muted small">HĐ {r.invoiceNo} · {fmtDate(r.receiptDate)}</div></> },
          { title: 'Tổng tiền', dataIndex: 'totalAmount', align: 'right', render: fmtMoney },
          { title: 'Còn nợ', dataIndex: 'remainingAmount', align: 'right', render: (v) => <b>{fmtMoney(v)}</b> },
          {
            title: 'Số tiền thanh toán', width: 190, render: (_, r) => (
              <InputNumber min={0} max={r.remainingAmount} value={amounts[r.id] ?? undefined} style={{ width: '100%' }} step={100000}
                onChange={(v) => setAmounts((a) => ({ ...a, [r.id]: v }))} {...moneyInput} addonAfter="đ" />
            ),
          },
        ]} />
      <Typography.Paragraph style={{ textAlign: 'right', fontSize: 16 }}>Tổng thanh toán: <b>{fmtMoney(total)}</b></Typography.Paragraph>
      <Form form={form} layout="vertical">
        <Row gutter={12}>
          <Col xs={24} sm={12}>
            <Form.Item name="paidAt" label="Ngày thanh toán" rules={[{ required: true, message: 'Chọn ngày thanh toán' }]}>
              <DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="method" label="Hình thức thanh toán" rules={[{ required: true, message: 'Chọn hình thức' }]}>
              <Select options={PAYMENT_METHODS.map((m) => ({ value: m, label: m }))} />
            </Form.Item>
          </Col>
          <Col span={24}><Form.Item name="note" label="Ghi chú"><Input.TextArea rows={2} maxLength={500} /></Form.Item></Col>
          <Col span={24}>
            <Form.Item label="Minh chứng thanh toán" required>
              <UploadField value={file} onChange={setFile} accept=".pdf,.jpg,.jpeg,.png,.webp" hint="Ủy nhiệm chi, ảnh chụp giao dịch, phiếu chi... (PDF/ảnh, tối đa 10MB)" />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
}

/** UC_16 – Chỉnh sửa khoản thanh toán (bắt buộc lý do). */
export function PaymentEditModal({ payment, maxAmount, onClose, onDone }: { payment: Payment | null; maxAmount: number; onClose: () => void; onDone: () => void }) {
  const [form] = Form.useForm();
  const [file, setFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!payment) return;
    form.setFieldsValue({ amount: payment.amount, paidAt: dayjs(payment.paidAt), method: payment.method, note: payment.note, reason: '' });
    setFile(null);
  }, [payment, form]);

  const submit = async () => {
    if (!payment) return;
    const v = await form.validateFields().catch(() => null);
    if (!v) return;
    setSaving(true);
    try {
      await api.put(`/payments/${payment.id}`, multipart({ ...v, paidAt: toIso(v.paidAt) }, { evidence: file }));
      ui.message.success('Đã cập nhật khoản thanh toán');
      onDone();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  return (
    <Modal open={!!payment} title={`Sửa khoản thanh toán ${payment?.code ?? ''}`} onCancel={onClose} onOk={submit} confirmLoading={saving}
      okText="Lưu thay đổi" cancelText="Hủy" destroyOnHidden>
      <Alert type="info" showIcon style={{ marginBottom: 12 }} message={`Số tiền tối đa có thể ghi nhận: ${fmtMoney(maxAmount)}`} />
      <Form form={form} layout="vertical">
        <Row gutter={12}>
          <Col span={12}><Form.Item name="amount" label="Số tiền" rules={[{ required: true, message: 'Nhập số tiền' }]}>
            <InputNumber min={1} max={maxAmount} style={{ width: '100%' }} {...moneyInput} addonAfter="đ" /></Form.Item></Col>
          <Col span={12}><Form.Item name="paidAt" label="Ngày thanh toán" rules={[{ required: true }]}><DatePicker format="DD/MM/YYYY" style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={12}><Form.Item name="method" label="Hình thức" rules={[{ required: true }]}><Select options={PAYMENT_METHODS.map((m) => ({ value: m, label: m }))} /></Form.Item></Col>
          <Col span={12}><Form.Item name="note" label="Ghi chú"><Input /></Form.Item></Col>
          <Col span={24}><Form.Item label="Thay minh chứng (không bắt buộc)">
            <UploadField value={file} onChange={setFile} accept=".pdf,.jpg,.jpeg,.png,.webp" dragger={false} label="Chọn minh chứng mới" /></Form.Item></Col>
          <Col span={24}><Form.Item name="reason" label="Lý do chỉnh sửa" rules={[{ required: true, whitespace: true, message: 'Vui lòng nhập lý do' }]}>
            <Input.TextArea rows={2} maxLength={500} /></Form.Item></Col>
        </Row>
      </Form>
    </Modal>
  );
}
