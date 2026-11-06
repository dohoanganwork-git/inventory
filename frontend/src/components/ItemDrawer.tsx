import { Button, Col, Descriptions, Drawer, Row, Space, Table, Tag } from 'antd';
import { ImportOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useFetch } from '../utils/useFetch';
import type { Item, Movement } from '../api/types';
import { ActiveTag, StockStatusTag } from './StatusTags';
import StatCard from './StatCard';
import { fmtDate, fmtDateTime, fmtMoney, fmtNumber } from '../utils/format';
import { movementLabel } from '../utils/labels';
import { useAuth } from '../auth/AuthContext';

const REF_LINK: Record<string, string> = { RECEIPT: '/phieu-nhap/', SALES: '/phieu-xuat/', STOCKTAKE: '/kiem-ke/' };

/** Chi tiết mặt hàng và thẻ kho (lịch sử biến động tồn). */
export default function ItemDrawer({ itemId, onClose }: { itemId: string | null; onClose: () => void }) {
  const nav = useNavigate();
  const { canSeeValues, hasRole } = useAuth();
  const { data: item } = useFetch<Item>(itemId ? `/items/${itemId}` : null);
  const { data: moves, loading } = useFetch<Movement[]>(itemId ? `/items/${itemId}/movements` : null);
  const shown = item && item.id === itemId ? item : null;

  return (
    <Drawer open={!!itemId} onClose={onClose} width={Math.min(860, window.innerWidth)} title={shown?.name ?? 'Chi tiết mặt hàng'}
      extra={shown && hasRole('STAFF', 'MANAGER') && (
        <Button type="primary" icon={<ImportOutlined />} onClick={() => nav(`/phieu-nhap/tao?supplierId=${shown.supplier?.id ?? ''}&itemId=${shown.id}`)}>Nhập thêm</Button>
      )}>
      {shown && (
        <>
          <Row gutter={[12, 12]}>
            <Col xs={12} md={6}><StatCard title="Tồn hiện tại" value={`${fmtNumber(shown.currentStock)}`} hint={shown.unit} tone={shown.stockStatus === 'LOW' ? 'danger' : 'info'} /></Col>
            <Col xs={12} md={6}><StatCard title="Mốc cảnh báo" value={shown.minStock} hint={shown.maxStock ? `Tối đa ${shown.maxStock}` : 'Không đặt mức tối đa'} /></Col>
            {canSeeValues && <Col xs={12} md={6}><StatCard title="Giá vốn gần nhất" value={fmtMoney(shown.lastCost)} /></Col>}
            {canSeeValues && <Col xs={12} md={6}><StatCard title="Giá trị tồn" value={fmtMoney(shown.stockValue)} /></Col>}
          </Row>
          <Descriptions size="small" column={{ xs: 1, md: 2 }} style={{ marginTop: 16 }} className="kv-grid" bordered>
            <Descriptions.Item label="Mã hàng"><b>{shown.code}</b></Descriptions.Item>
            <Descriptions.Item label="Mã trên POS">{shown.posCode}</Descriptions.Item>
            <Descriptions.Item label="Loại hàng">{shown.category?.name}</Descriptions.Item>
            <Descriptions.Item label="Nhà cung cấp">{shown.supplier?.name ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Giá bán">{fmtMoney(shown.salePrice)}</Descriptions.Item>
            <Descriptions.Item label="Trạng thái">
              <Space><StockStatusTag status={shown.stockStatus} stock={shown.currentStock} /><ActiveTag active={shown.active} /></Space>
            </Descriptions.Item>
          </Descriptions>
          <h3 style={{ marginTop: 24 }}>Thẻ kho – lịch sử biến động tồn</h3>
          <Table<Movement> size="small" rowKey="id" loading={loading} dataSource={moves ?? []} scroll={{ x: 720 }}
            pagination={{ pageSize: 10, showSizeChanger: false }}
            columns={[
              { title: 'Ngày', dataIndex: 'movementDate', width: 100, render: fmtDate },
              { title: 'Nghiệp vụ', dataIndex: 'type', render: (v) => movementLabel[v] ?? v },
              { title: 'Chứng từ', dataIndex: 'refCode', render: (v, r) => (v && r.refType && r.refId
                ? <Button type="link" size="small" style={{ padding: 0 }} onClick={() => nav(REF_LINK[r.refType!] + r.refId)}>{v}</Button> : '—') },
              { title: 'Thay đổi', dataIndex: 'quantityChange', align: 'right', render: (v: number) => <Tag color={v > 0 ? 'blue' : v < 0 ? 'orange' : 'default'}>{v > 0 ? `+${v}` : v}</Tag> },
              { title: 'Tồn trước', dataIndex: 'stockBefore', align: 'right' },
              { title: 'Tồn sau', dataIndex: 'stockAfter', align: 'right', render: (v) => <b>{v}</b> },
              { title: 'Người thực hiện', dataIndex: 'createdBy', render: (v, r) => <>{v ?? '—'}<div className="muted small">{fmtDateTime(r.createdAt)}</div></> },
            ]} />
        </>
      )}
    </Drawer>
  );
}
