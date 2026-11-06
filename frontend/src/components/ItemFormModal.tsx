import { Col, Form, Input, InputNumber, Modal, Row, Select } from 'antd';
import { useEffect, useState } from 'react';
import type { Category, Item, Supplier } from '../api/types';
import { api, showError } from '../api/client';
import { ui } from '../utils/globals';

interface Props { open: boolean; item?: Item | null; categories: Category[]; suppliers: Supplier[]; onClose: () => void; onSaved: () => void }

export default function ItemFormModal({ open, item, categories, suppliers, onClose, onSaved }: Props) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (item) {
      form.setFieldsValue({ ...item, categoryId: item.category?.id, supplierId: item.supplier?.id });
    } else {
      form.setFieldsValue({ unit: 'Chai', minStock: 10 });
    }
  }, [open, item, form]);

  const save = async () => {
    const v = await form.validateFields();
    setSaving(true);
    try {
      if (item) await api.put(`/items/${item.id}`, v);
      else await api.post('/items', v);
      ui.message.success(item ? 'Cập nhật mặt hàng thành công' : 'Thêm mặt hàng thành công');
      onSaved();
    } catch (e) { showError(e); } finally { setSaving(false); }
  };

  return (
    <Modal open={open} title={item ? `Chỉnh sửa: ${item.name}` : 'Thêm mặt hàng mới'} onOk={save} onCancel={onClose}
      okText="Lưu" cancelText="Hủy" confirmLoading={saving} width={680} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item name="name" label="Tên mặt hàng" rules={[{ required: true, message: 'Vui lòng nhập tên mặt hàng' }]}>
          <Input placeholder="vd: Bia Tê Tê Craft Beer Pale Ale 330ml" />
        </Form.Item>
        <Row gutter={12}>
          <Col xs={24} sm={12}>
            <Form.Item name="code" label="Mã hàng" rules={[{ required: true, message: 'Vui lòng nhập mã hàng' }]}>
              <Input placeholder="vd: TT-PALE" style={{ textTransform: 'uppercase' }} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="posCode" label="Mã trên POS (Sapo)" tooltip="Dùng để khớp file doanh số POS cuối ngày" rules={[{ required: true, message: 'Vui lòng nhập mã POS' }]}>
              <Input placeholder="vd: SKU1001" />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="categoryId" label="Loại hàng" rules={[{ required: true, message: 'Vui lòng chọn loại hàng' }]}
              extra={categories.length === 0 ? 'Chưa có loại hàng – hãy thêm loại hàng trước' : undefined}>
              <Select showSearch optionFilterProp="label" placeholder="Chọn loại hàng"
                options={categories.filter((c) => c.active || c.id === item?.category?.id).map((c) => ({ value: c.id, label: c.name }))} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item name="supplierId" label="Nhà cung cấp chính">
              <Select allowClear showSearch optionFilterProp="label" placeholder="Chọn nhà cung cấp"
                options={suppliers.filter((s) => s.active || s.id === item?.supplier?.id).map((s) => ({ value: s.id, label: s.name }))} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={6}>
            <Form.Item name="unit" label="Đơn vị tính" rules={[{ required: true, message: 'Nhập ĐVT' }]}>
              <Select options={['Chai', 'Lon', 'Thùng', 'Đĩa', 'Cái', 'Hộp', 'Túi'].map((u) => ({ value: u, label: u }))} showSearch />
            </Form.Item>
          </Col>
          <Col xs={12} sm={6}>
            <Form.Item name="minStock" label="Mốc tồn thấp" tooltip="Hệ thống cảnh báo khi tồn kho ≤ mốc này" rules={[{ required: true, message: 'Nhập mốc' }]}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={6}>
            <Form.Item name="maxStock" label="Mức tồn tối đa" tooltip="Không bắt buộc – cảnh báo khi tồn vượt mức">
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
          </Col>
          <Col xs={12} sm={6}>
            <Form.Item name="salePrice" label="Giá bán (đ)">
              <InputNumber min={0} step={1000} style={{ width: '100%' }} formatter={(v) => `${v ?? ''}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}
                parser={(v) => Number((v ?? '').replace(/\./g, '')) as unknown as 0} />
            </Form.Item>
          </Col>
        </Row>
      </Form>
    </Modal>
  );
}
