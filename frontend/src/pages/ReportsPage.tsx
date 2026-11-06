import { Alert, Button, Card, Col, DatePicker, Empty, Row, Segmented, Space, Table, Tag } from 'antd';
import { FileExcelOutlined, FilePdfOutlined, ReloadOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../components/PageHeader';
import { ColumnChart, RankChart } from '../components/Charts';
import { useFetch } from '../utils/useFetch';
import { download } from '../api/client';
import type { ReportColumn, ReportTable } from '../api/types';
import { fmtDate, fmtMoney, fmtMoneyShort, fmtNumber, toIso } from '../utils/format';
import { useAuth } from '../auth/AuthContext';

const PRESETS: { label: string; value: [Dayjs, Dayjs] }[] = [
  { label: 'Tháng này', value: [dayjs().startOf('month'), dayjs()] },
  { label: 'Tháng trước', value: [dayjs().subtract(1, 'month').startOf('month'), dayjs().subtract(1, 'month').endOf('month')] },
  { label: '7 ngày qua', value: [dayjs().subtract(6, 'day'), dayjs()] },
  { label: '30 ngày qua', value: [dayjs().subtract(29, 'day'), dayjs()] },
  { label: 'Quý này', value: [dayjs().month(Math.floor(dayjs().month() / 3) * 3).startOf('month'), dayjs()] },
];

function cell(col: ReportColumn, v: unknown) {
  if (v === null || v === undefined || v === '') return '';
  switch (col.type) {
    case 'money': return fmtMoney(Number(v));
    case 'int': return fmtNumber(Number(v));
    case 'percent': return `${Number(v).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}%`;
    case 'date': return fmtDate(String(v));
    default:
      if (col.key === 'status') {
        const s = String(v);
        const color = s === 'Tồn thấp' ? 'warning' : s === 'Vượt mức' ? 'purple' : s === 'Hoàn tất' || s === 'Bình thường' ? 'success' : s.startsWith('Chờ') ? 'processing' : undefined;
        return <Tag color={color}>{s}</Tag>;
      }
      return String(v);
  }
}

export default function ReportsPage() {
  const { canSeeValues } = useAuth();
  const [params, setParams] = useSearchParams();
  const { data: types } = useFetch<{ key: string; title: string }[]>('/reports');
  const type = params.get('loai') ?? 'ton-kho';
  const [range, setRange] = useState<[Dayjs, Dayjs]>([dayjs().subtract(29, 'day'), dayjs()]);
  const usesRange = type !== 'ton-kho';
  const query = usesRange ? { from: toIso(range[0]), to: toIso(range[1]) } : {};
  const { data: report, loading, reload } = useFetch<ReportTable>(`/reports/${type}`, query);

  const columns = useMemo(() => (report?.columns ?? []).map((c) => ({
    title: c.title, dataIndex: c.key, key: c.key,
    align: (['int', 'money', 'percent'].includes(c.type) ? 'right' : c.type === 'date' ? 'center' : 'left') as 'right' | 'left' | 'center',
    width: c.key === 'name' ? 260 : c.type === 'money' ? 140 : c.type === 'text' ? 130 : 110,
    render: (v: unknown) => cell(c, v),
    sorter: ['int', 'money', 'percent'].includes(c.type) ? (a: Record<string, unknown>, b: Record<string, unknown>) => Number(a[c.key] ?? 0) - Number(b[c.key] ?? 0) : undefined,
  })), [report]);

  const charts = () => {
    if (!report) return null;
    const ex = report.extra as Record<string, Record<string, unknown>[]>;
    if (report.key === 'tieu-thu') {
      const daily = (ex.daily ?? []).map((d) => ({ ...d, label: dayjs(String(d.date)).format('DD/MM') }));
      const byCat = (ex.byCategory ?? []).sort((a, b) => Number(b.quantity) - Number(a.quantity));
      return (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} xl={14}><Card className="section-card" title="Số lượng bán theo ngày"><ColumnChart data={daily as never} xKey="label" yKey="quantity" valueLabel="Số lượng bán" /></Card></Col>
          <Col xs={24} xl={10}><Card className="section-card" title={canSeeValues ? 'Doanh thu theo loại hàng' : 'Số lượng bán theo loại hàng'}>
            <RankChart data={byCat as never} labelKey="category" valueKey={canSeeValues ? 'revenue' : 'quantity'}
              valueLabel={canSeeValues ? 'Doanh thu' : 'Số lượng'} format={canSeeValues ? (v) => fmtMoneyShort(v) : undefined} /></Card></Col>
          {(ex.unsold ?? []).length > 0 && (
            <Col span={24}><Alert type="warning" showIcon message={`${ex.unsold.length} mặt hàng không bán được trong kỳ (hàng khó bán)`}
              description={ex.unsold.map((u) => `${u.name} (tồn ${u.stock})`).join(' · ')} /></Col>
          )}
        </Row>
      );
    }
    if (report.key === 'ton-kho') {
      const byCat = (ex.byCategory ?? []).sort((a, b) => Number(b[canSeeValues ? 'value' : 'quantity']) - Number(a[canSeeValues ? 'value' : 'quantity']));
      return <Card className="section-card" style={{ marginBottom: 16 }} title={canSeeValues ? 'Giá trị tồn kho theo loại hàng' : 'Số lượng tồn theo loại hàng'}>
        <RankChart data={byCat as never} labelKey="category" valueKey={canSeeValues ? 'value' : 'quantity'} valueLabel={canSeeValues ? 'Giá trị tồn' : 'Số lượng'}
          format={canSeeValues ? (v) => fmtMoneyShort(v) : undefined} /></Card>;
    }
    if (report.key === 'cong-no') {
      const rows = report.rows.filter((r) => Number(r.closing) > 0);
      return <Card className="section-card" style={{ marginBottom: 16 }} title="Nợ cuối kỳ theo nhà cung cấp">
        <RankChart data={rows as never} labelKey="name" valueKey="closing" valueLabel="Nợ cuối kỳ" format={(v) => fmtMoneyShort(v)} /></Card>;
    }
    if (report.key === 'kiem-ke') {
      const rows = [...report.rows].reverse().map((r) => ({ ...r, label: `${dayjs(String(r.date)).format('DD/MM')}` }));
      return <Card className="section-card" style={{ marginBottom: 16 }} title="Tỉ lệ chính xác kho qua các đợt kiểm kê (%)">
        <ColumnChart data={rows as never} xKey="label" yKey="accuracy" valueLabel="Tỉ lệ chính xác" format={(v) => `${v}%`} /></Card>;
    }
    return null;
  };

  return (
    <>
      <PageHeader title="Báo cáo thống kê" subtitle={canSeeValues ? 'Tổng hợp tồn kho, nhập – xuất – tồn, tiêu thụ, công nợ và kiểm kê. Xuất Excel hoặc PDF.' : 'Nhân viên xem số liệu số lượng; số liệu giá trị và công nợ chỉ hiển thị với Quản lý và Kế toán.'} />
      <Card className="section-card" style={{ marginBottom: 16 }}>
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <div style={{ overflowX: 'auto' }}>
            <Segmented value={type} onChange={(v) => setParams({ loai: String(v) })}
              options={(types ?? []).map((t) => ({ label: (() => { const l = t.title.replace('Báo cáo ', ''); return l.charAt(0).toUpperCase() + l.slice(1); })(), value: t.key }))} />
          </div>
          <div className="filter-bar" style={{ marginBottom: 0 }}>
            {usesRange && <DatePicker.RangePicker format="DD/MM/YYYY" value={range} allowClear={false} presets={PRESETS}
              onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])} />}
            <Button icon={<ReloadOutlined />} onClick={reload}>Làm mới</Button>
            <div style={{ flex: 1 }} />
            <Button icon={<FileExcelOutlined />} onClick={() => download(`/reports/${type}/export`, { ...query, format: 'xlsx' })}>Xuất Excel</Button>
            <Button icon={<FilePdfOutlined />} onClick={() => download(`/reports/${type}/export`, { ...query, format: 'pdf' })}>Xuất PDF</Button>
          </div>
        </Space>
      </Card>
      {report && report.key === type && charts()}
      <Card className="section-card" title={report?.title} extra={<span className="muted small">{report?.subtitle}</span>}>
        {report && report.rows.length === 0 && !loading ? <Empty description="Không có dữ liệu trong khoảng thời gian đã chọn" /> : (
          <Table rowKey="_key" loading={loading} dataSource={report?.key === type ? report.rows.map((r, i) => ({ ...r, _key: i })) : []} columns={columns}
            scroll={{ x: columns.reduce((w, c) => w + (c.width ?? 120), 0) }} pagination={{ pageSize: 50, hideOnSinglePage: true }} size="small"
            summary={() => report && report.totals && Object.keys(report.totals).length > 0 ? (
              <Table.Summary fixed>
                <Table.Summary.Row style={{ background: '#f6f8fb' }}>
                  {report.columns.map((c, i) => (
                    <Table.Summary.Cell key={c.key} index={i} align={['int', 'money', 'percent'].includes(c.type) ? 'right' : 'left'}>
                      <b>{cell(c, report.totals[c.key])}</b>
                    </Table.Summary.Cell>
                  ))}
                </Table.Summary.Row>
              </Table.Summary>
            ) : null} />
        )}
      </Card>
    </>
  );
}
