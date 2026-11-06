import { Alert, Button, Card, Col, Empty, List, Row, Skeleton, Space, Table, Tag, Typography } from 'antd';
import {
  AppstoreOutlined, WarningOutlined, DollarOutlined, DatabaseOutlined, ImportOutlined, ExportOutlined,
  AuditOutlined, ReconciliationOutlined, RightOutlined, CalendarOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useFetch } from '../utils/useFetch';
import type { Dashboard, Item } from '../api/types';
import StatCard from '../components/StatCard';
import { StockStatusTag } from '../components/StatusTags';
import { ColumnChart, RankChart } from '../components/Charts';
import { fmtDate, fmtMoney, fmtMoneyShort, fmtNumber } from '../utils/format';
import { roleLabel } from '../utils/labels';

interface Task { text: string; to: string; tone: 'danger' | 'warning' | 'info'; icon: JSX.Element }

export default function DashboardPage() {
  const { user, hasRole, canSeeValues } = useAuth();
  const nav = useNavigate();
  const { data, loading } = useFetch<Dashboard>('/dashboard');

  if (loading || !data) return <Skeleton active paragraph={{ rows: 10 }} />;

  const tasks: Task[] = [];
  if (hasRole('MANAGER')) {
    if (!data.salesUpToDate) tasks.push({ text: `Nhập doanh số POS (mới có đến ${fmtDate(data.latestSalesDate)})`, to: '/phieu-xuat/nhap-pos', tone: 'warning', icon: <ExportOutlined /> });
    if (data.pendingStocktakes) tasks.push({ text: `${data.pendingStocktakes} đợt kiểm kê chờ bạn xem xét`, to: '/kiem-ke', tone: 'danger', icon: <AuditOutlined /> });
  }
  if (hasRole('STAFF')) {
    if (data.recountStocktakes) tasks.push({ text: `Quản lý yêu cầu kiểm kê lại (${data.recountStocktakes} đợt)`, to: '/kiem-ke', tone: 'danger', icon: <AuditOutlined /> });
  }
  if (hasRole('STAFF', 'MANAGER') && data.lowStockCount) {
    tasks.push({ text: `${data.lowStockCount} mặt hàng sắp hết / hết hàng cần đặt thêm`, to: '/canh-bao', tone: 'warning', icon: <WarningOutlined /> });
  }
  if (hasRole('ACCOUNTANT', 'MANAGER')) {
    if (data.overdueDebt && data.overdueDebt > 0) tasks.push({ text: `Công nợ quá hạn ${fmtMoney(data.overdueDebt)}`, to: '/cong-no', tone: 'danger', icon: <DollarOutlined /> });
    if (hasRole('ACCOUNTANT') && data.openDebtReceipts) tasks.push({ text: `${data.openDebtReceipts} phiếu nhập còn công nợ`, to: '/cong-no', tone: 'info', icon: <DollarOutlined /> });
    if (data.openDebtRecons) tasks.push({ text: `${data.openDebtRecons} kỳ đối soát công nợ đang xử lý`, to: '/doi-soat-cong-no', tone: 'info', icon: <ReconciliationOutlined /> });
  }

  const hour = dayjs().hour();
  const greet = hour < 11 ? 'Chào buổi sáng' : hour < 14 ? 'Chào buổi trưa' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';
  const salesData = data.sales14.map((d) => ({ ...d, label: dayjs(d.date).format('DD/MM') }));

  return (
    <>
      <div className="page-header">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>{greet}, {user?.fullName}</Typography.Title>
          <div className="page-subtitle"><CalendarOutlined /> {dayjs().format('dddd, DD/MM/YYYY')} · Vai trò: {roleLabel[user?.role ?? '']}</div>
        </div>
        <Space wrap>
          {hasRole('STAFF', 'MANAGER') && <Button type="primary" icon={<ImportOutlined />} onClick={() => nav('/phieu-nhap/tao')}>Tạo phiếu nhập</Button>}
          {hasRole('MANAGER') && <Button icon={<ExportOutlined />} onClick={() => nav('/phieu-xuat/nhap-pos')}>Nhập doanh số POS</Button>}
          {hasRole('STAFF') && <Button icon={<AuditOutlined />} onClick={() => nav('/kiem-ke')}>Kiểm kê</Button>}
          {hasRole('ACCOUNTANT') && <Button type="primary" icon={<DollarOutlined />} onClick={() => nav('/cong-no')}>Thanh toán công nợ</Button>}
        </Space>
      </div>

      {hasRole('MANAGER') && !data.salesUpToDate && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }}
          message={`Doanh số POS mới được nhập đến ngày ${fmtDate(data.latestSalesDate)}. Tồn kho có thể chưa phản ánh hàng đã bán.`}
          action={<Button size="small" onClick={() => nav('/phieu-xuat/nhap-pos')}>Nhập ngay</Button>} />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={12} lg={6}>
          <StatCard title="Mặt hàng kinh doanh" value={fmtNumber(data.itemCount)} icon={<AppstoreOutlined />} tone="info"
            hint={`${data.overStockCount} mặt hàng tồn vượt mức`} onClick={() => nav('/ton-kho')} />
        </Col>
        <Col xs={12} lg={6}>
          <StatCard title="Sắp hết / hết hàng" value={fmtNumber(data.lowStockCount)} icon={<WarningOutlined />}
            tone={data.lowStockCount ? 'danger' : 'success'} hint={`${data.outOfStockCount} mặt hàng đã hết`}
            onClick={() => nav(hasRole('ACCOUNTANT') ? '/ton-kho?status=LOW' : '/canh-bao')} />
        </Col>
        {canSeeValues ? (
          <>
            <Col xs={12} lg={6}>
              <StatCard title="Giá trị tồn kho" value={fmtMoneyShort(data.stockValue)} icon={<DatabaseOutlined />} tone="info"
                hint={fmtMoney(data.stockValue)} onClick={() => nav('/bao-cao?loai=ton-kho')} />
            </Col>
            <Col xs={12} lg={6}>
              <StatCard title="Công nợ phải trả" value={fmtMoneyShort(data.openDebt)} icon={<DollarOutlined />}
                tone={data.overdueDebt ? 'warning' : 'info'}
                hint={data.overdueDebt ? <span style={{ color: '#c4320a' }}>Quá hạn: {fmtMoney(data.overdueDebt)}</span> : `${data.openDebtReceipts} phiếu chưa thanh toán hết`}
                onClick={() => nav('/cong-no')} />
            </Col>
          </>
        ) : (
          <>
            <Col xs={12} lg={6}>
              <StatCard title="Kiểm kê cần làm lại" value={data.recountStocktakes} icon={<AuditOutlined />}
                tone={data.recountStocktakes ? 'danger' : 'success'} hint={`${data.pendingStocktakes} đợt đang chờ Quản lý`} onClick={() => nav('/kiem-ke')} />
            </Col>
            <Col xs={12} lg={6}>
              <StatCard title="Doanh số POS" value={data.latestSalesDate ? dayjs(data.latestSalesDate).format('DD/MM') : '—'} icon={<ExportOutlined />}
                tone={data.salesUpToDate ? 'success' : 'warning'} hint="Ngày doanh số gần nhất đã nhập" onClick={() => nav('/phieu-xuat')} />
            </Col>
          </>
        )}
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} xl={16}>
          <Card className="section-card" title="Số lượng sản phẩm bán ra – 14 ngày gần nhất"
            extra={<Button type="link" onClick={() => nav('/bao-cao?loai=tieu-thu')}>Báo cáo tiêu thụ <RightOutlined /></Button>}>
            <ColumnChart data={salesData} xKey="label" yKey="quantity" valueLabel="Số lượng bán" />
          </Card>
        </Col>
        <Col xs={24} xl={8}>
          <Card className="section-card" title="Việc cần làm" style={{ height: '100%' }}>
            {tasks.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Không có việc tồn đọng 🎉" /> : (
              <List dataSource={tasks} renderItem={(t) => (
                <List.Item className="row-clickable" onClick={() => nav(t.to)} actions={[<RightOutlined key="go" />]}>
                  <Space>
                    <Tag color={t.tone === 'danger' ? 'error' : t.tone === 'warning' ? 'warning' : 'processing'} icon={t.icon} style={{ margin: 0 }} />
                    <span>{t.text}</span>
                  </Space>
                </List.Item>
              )} />
            )}
          </Card>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} xl={12}>
          <Card className="section-card" title="Bán chạy nhất 30 ngày (số lượng)">
            <RankChart data={data.topItems} labelKey="name" valueKey="quantity" valueLabel="Số lượng bán" />
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card className="section-card" title="Mặt hàng cần bổ sung"
            extra={hasRole('STAFF', 'MANAGER') && <Button type="link" onClick={() => nav('/canh-bao')}>Xem tất cả <RightOutlined /></Button>}>
            <Table<Item> size="small" rowKey="id" pagination={false} dataSource={data.lowStockItems}
              locale={{ emptyText: 'Không có mặt hàng nào dưới mốc cảnh báo' }}
              scroll={{ x: 460 }}
              columns={[
                { title: 'Mặt hàng', dataIndex: 'name', render: (v, r) => <><div>{v}</div><div className="muted small">{r.supplier?.name}</div></> },
                { title: 'Tồn', dataIndex: 'currentStock', align: 'right', width: 70, render: (v, r) => <b>{v} <span className="muted small">{r.unit}</span></b> },
                { title: 'Mốc', dataIndex: 'minStock', align: 'right', width: 60 },
                { title: 'Trạng thái', width: 120, render: (_, r) => <StockStatusTag status={r.stockStatus} stock={r.currentStock} /> },
                ...(hasRole('STAFF', 'MANAGER') ? [{
                  title: '', width: 90, render: (_: unknown, r: Item) => (
                    <Button size="small" onClick={() => nav(`/phieu-nhap/tao?supplierId=${r.supplier?.id ?? ''}&itemId=${r.id}`)}>Nhập thêm</Button>),
                }] : []),
              ]} />
          </Card>
        </Col>
      </Row>
    </>
  );
}
