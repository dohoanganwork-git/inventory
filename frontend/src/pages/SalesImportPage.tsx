import { Alert, Button, Card, Col, DatePicker, Result, Row, Space, Steps, Table, Tag } from 'antd';
import { DownloadOutlined, CheckOutlined, WarningOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import UploadField from '../components/UploadField';
import StatCard from '../components/StatCard';
import { api, download, showError } from '../api/client';
import type { Sales, SalesPreview, SalesPreviewRow } from '../api/types';
import { ui } from '../utils/globals';
import { fmtDate, fmtMoney, fmtNumber, toIso } from '../utils/format';

export default function SalesImportPage() {
  const nav = useNavigate();
  const [date, setDate] = useState<Dayjs | null>(dayjs().subtract(1, 'day'));
  const [file, setFile] = useState<File | null>(null);
  const [preview, setPreview] = useState<SalesPreview | null>(null);
  const [result, setResult] = useState<Sales | null>(null);
  const [loading, setLoading] = useState(false);
  const step = result ? 2 : preview ? 1 : 0;

  const form = () => {
    const fd = new FormData();
    fd.append('file', file!);
    return fd;
  };

  const doPreview = async () => {
    if (!date) { ui.message.warning('Vui lòng chọn ngày doanh số'); return; }
    if (!file) { ui.message.warning('Vui lòng chọn file doanh số'); return; }
    setLoading(true);
    try {
      const { data } = await api.post<SalesPreview>('/sales/preview', form(), { params: { salesDate: toIso(date) } });
      setPreview(data);
    } catch (e) { showError(e); } finally { setLoading(false); }
  };

  const confirm = async () => {
    setLoading(true);
    try {
      const { data } = await api.post<Sales>('/sales', form(), { params: { salesDate: toIso(date) } });
      setResult(data);
      ui.message.success('Nhập doanh số thành công, tồn kho đã được cập nhật');
    } catch (e) { showError(e); } finally { setLoading(false); }
  };

  const reset = () => { setPreview(null); setResult(null); setFile(null); };

  return (
    <>
      <PageHeader back="/phieu-xuat" title="Nhập doanh số POS cuối ngày"
        subtitle="Tải file doanh số xuất từ Sapo POS (Excel/CSV). Hệ thống kiểm tra, cho xem trước rồi mới trừ tồn kho."
        extra={<Button icon={<DownloadOutlined />} onClick={() => download('/templates/pos')}>Tải file mẫu</Button>} />
      <Card className="section-card">
        <Steps current={step} style={{ maxWidth: 760, margin: '0 auto 24px' }} responsive
          items={[{ title: 'Chọn ngày & file' }, { title: 'Kiểm tra & xem trước' }, { title: 'Hoàn tất' }]} />

        {step === 0 && (
          <Row gutter={[24, 16]} justify="center">
            <Col xs={24} md={8}>
              <div style={{ marginBottom: 8, fontWeight: 500 }}>Ngày doanh số</div>
              <DatePicker value={date} onChange={setDate} format="DD/MM/YYYY" style={{ width: '100%' }} size="large"
                disabledDate={(d) => d.isAfter(dayjs(), 'day')} />
              <Alert style={{ marginTop: 16 }} type="info" showIcon
                message="Lưu ý"
                description={<ul style={{ paddingLeft: 18, margin: 0 }}>
                  <li>Mỗi ngày chỉ nhập một lần để tránh trừ tồn hai lần.</li>
                  <li>Mã hàng trong file phải trùng "Mã POS" trong danh mục.</li>
                  <li>Bán vượt tồn: tồn đặt về 0 và cảnh báo cho Quản lý.</li>
                </ul>} />
            </Col>
            <Col xs={24} md={12}>
              <div style={{ marginBottom: 8, fontWeight: 500 }}>File doanh số</div>
              <UploadField value={file} onChange={setFile} accept=".xlsx,.xls,.csv" hint="Hỗ trợ .xlsx, .xls, .csv – cột bắt buộc: Mã hàng POS, Số lượng" />
              <Button type="primary" size="large" block style={{ marginTop: 16 }} loading={loading} onClick={doPreview}>Kiểm tra file</Button>
            </Col>
          </Row>
        )}

        {step === 1 && preview && (
          <>
            <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
              <Col xs={12} md={6}><StatCard title="Ngày doanh số" value={fmtDate(preview.salesDate)} hint={preview.fileName} /></Col>
              <Col xs={12} md={6}><StatCard title="Tổng số lượng bán" value={fmtNumber(preview.totalQuantity)} hint={`${preview.rows.length} mặt hàng · ${fmtMoney(preview.totalAmount)}`} /></Col>
              <Col xs={12} md={6}><StatCard title="Bán vượt tồn" value={preview.overItems} tone={preview.overItems ? 'danger' : 'success'} hint="mặt hàng" /></Col>
              <Col xs={12} md={6}><StatCard title="Sẽ dưới mốc cảnh báo" value={preview.lowStockItems} tone={preview.lowStockItems ? 'warning' : 'success'} hint="mặt hàng" /></Col>
            </Row>
            {preview.errors.length > 0 && (
              <Alert type="error" showIcon style={{ marginBottom: 16 }} message="File chưa thể nhập – vui lòng sửa file hoặc bổ sung danh mục rồi tải lại"
                description={<ul style={{ margin: 0, paddingLeft: 18 }}>{preview.errors.map((e) => <li key={e}>{e}</li>)}</ul>} />
            )}
            <Table<SalesPreviewRow> size="small" rowKey="rowNumber" dataSource={preview.rows} pagination={false} scroll={{ x: 860, y: 460 }}
              rowClassName={(r) => (!r.itemId ? 'row-danger' : r.overQuantity > 0 ? 'row-danger' : r.lowStockAfter ? 'row-warn' : '')}
              columns={[
                { title: 'Dòng', dataIndex: 'rowNumber', width: 60 },
                { title: 'Mã POS', dataIndex: 'posCode', width: 110 },
                { title: 'Mặt hàng', render: (_, r) => (r.itemId ? r.itemName : <Tag color="error">Không có trong danh mục{r.fileName ? `: ${r.fileName}` : ''}</Tag>) },
                { title: 'SL bán', dataIndex: 'quantity', align: 'right', width: 80 },
                { title: 'Đơn giá', dataIndex: 'unitPrice', align: 'right', width: 110, render: fmtMoney },
                { title: 'Tồn hiện tại', dataIndex: 'currentStock', align: 'right', width: 110 },
                { title: 'Tồn sau khi trừ', dataIndex: 'stockAfter', align: 'right', width: 120, render: (v) => <b>{v}</b> },
                { title: 'Ghi chú', width: 170, render: (_, r) => (r.overQuantity > 0
                  ? <Tag color="error" icon={<WarningOutlined />}>Bán vượt tồn {r.overQuantity}</Tag>
                  : r.lowStockAfter && r.itemId ? <Tag color="warning">Dưới mốc cảnh báo</Tag> : null) },
              ]} />
            <Space style={{ marginTop: 16, width: '100%', justifyContent: 'flex-end' }} wrap>
              <Button onClick={reset}>Chọn file khác</Button>
              <Button type="primary" size="large" icon={<CheckOutlined />} disabled={!preview.canImport} loading={loading} onClick={confirm}>
                Xác nhận nhập & trừ tồn kho
              </Button>
            </Space>
          </>
        )}

        {step === 2 && result && (
          <Result status="success" title={`Đã tạo phiếu xuất bán ${result.code}`}
            subTitle={`Doanh số ngày ${fmtDate(result.salesDate)}: ${result.lineCount} mặt hàng, ${fmtNumber(result.totalQuantity)} sản phẩm. File gốc đã được lưu.${result.overQuantity ? ` Có ${result.overQuantity} sản phẩm bán vượt tồn – đã gửi cảnh báo.` : ''}`}
            extra={[
              <Button type="primary" key="view" onClick={() => nav(`/phieu-xuat/${result.id}`)}>Xem phiếu xuất</Button>,
              <Button key="more" onClick={() => { reset(); setDate(dayjs(result.salesDate).add(1, 'day').isAfter(dayjs(), 'day') ? dayjs() : dayjs(result.salesDate).add(1, 'day')); }}>Nhập ngày tiếp theo</Button>,
              <Button key="alerts" onClick={() => nav('/canh-bao')}>Xem cảnh báo tồn kho</Button>,
            ]} />
        )}
      </Card>
    </>
  );
}
