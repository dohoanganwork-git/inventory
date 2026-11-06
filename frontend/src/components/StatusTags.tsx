import { Tag } from 'antd';
import {
  CheckCircleOutlined, ClockCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
  MinusCircleOutlined, SyncOutlined, WarningOutlined, StopOutlined, FileSearchOutlined,
} from '@ant-design/icons';
import { reconResultLabel, roleColor, roleLabel } from '../utils/labels';

// Trạng thái luôn kèm biểu tượng + nhãn chữ, không chỉ dựa vào màu sắc.

export const RoleTag = ({ role }: { role: string }) => <Tag color={roleColor[role]}>{roleLabel[role] ?? role}</Tag>;

export const ActiveTag = ({ active, on = 'Đang sử dụng', off = 'Ngừng sử dụng' }: { active: boolean; on?: string; off?: string }) =>
  active ? <Tag icon={<CheckCircleOutlined />} color="success">{on}</Tag> : <Tag icon={<StopOutlined />}>{off}</Tag>;

export const StockStatusTag = ({ status, stock }: { status: string; stock?: number }) => {
  if (status === 'LOW') {
    return stock === 0
      ? <Tag icon={<CloseCircleOutlined />} color="error">Hết hàng</Tag>
      : <Tag icon={<WarningOutlined />} color="warning">Sắp hết</Tag>;
  }
  if (status === 'OVER') return <Tag icon={<ExclamationCircleOutlined />} color="purple">Vượt mức</Tag>;
  return <Tag icon={<CheckCircleOutlined />} color="success">Bình thường</Tag>;
};

export const DocStatusTag = ({ status }: { status: string }) =>
  status === 'CANCELLED' ? <Tag icon={<CloseCircleOutlined />} color="default">Đã hủy</Tag> : <Tag icon={<CheckCircleOutlined />} color="blue">Đã ghi nhận</Tag>;

export const DebtStatusTag = ({ status, overdue }: { status?: string; overdue?: boolean }) => {
  if (!status) return null;
  if (status === 'PAID') return <Tag icon={<CheckCircleOutlined />} color="success">Đã thanh toán</Tag>;
  const t = status === 'PARTIAL'
    ? <Tag icon={<MinusCircleOutlined />} color="processing">Trả một phần</Tag>
    : <Tag icon={<ClockCircleOutlined />} color="warning">Chưa thanh toán</Tag>;
  return overdue ? <>{t}<Tag icon={<ExclamationCircleOutlined />} color="error">Quá hạn</Tag></> : t;
};

export const StocktakeStatusTag = ({ status }: { status: string }) => {
  if (status === 'PENDING_REVIEW') return <Tag icon={<FileSearchOutlined />} color="warning">Chờ Quản lý xem xét</Tag>;
  if (status === 'RECOUNT_REQUESTED') return <Tag icon={<SyncOutlined />} color="processing">Chờ kiểm kê lại</Tag>;
  return <Tag icon={<CheckCircleOutlined />} color="success">Hoàn tất</Tag>;
};

export const ReconStatusTag = ({ status }: { status: string }) =>
  status === 'COMPLETED'
    ? <Tag icon={<CheckCircleOutlined />} color="success">Đã hoàn tất (khóa kỳ)</Tag>
    : <Tag icon={<SyncOutlined />} color="processing">Đang đối soát</Tag>;

export const ReconResultTag = ({ result }: { result: string }) =>
  result === 'MATCHED'
    ? <Tag icon={<CheckCircleOutlined />} color="success">Khớp</Tag>
    : <Tag icon={<ExclamationCircleOutlined />} color="error">{reconResultLabel[result] ?? result}</Tag>;

export const ContractTag = ({ status }: { status: string }) => {
  if (status === 'EXPIRED') return <Tag icon={<CloseCircleOutlined />} color="error">Hết hạn</Tag>;
  if (status === 'EXPIRING') return <Tag icon={<WarningOutlined />} color="warning">Sắp hết hạn</Tag>;
  if (status === 'ACTIVE') return <Tag icon={<CheckCircleOutlined />} color="success">Còn hiệu lực</Tag>;
  return <Tag>Chưa có hợp đồng</Tag>;
};

export const VarianceText = ({ v }: { v: number }) => {
  if (v === 0) return <span style={{ color: '#52514e' }}>0</span>;
  return <b style={{ color: v > 0 ? '#1c5cab' : '#c4320a' }}>{v > 0 ? `+${v} (thừa)` : `${v} (thiếu)`}</b>;
};
