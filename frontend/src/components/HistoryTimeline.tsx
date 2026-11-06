import { Empty, Timeline, Typography } from 'antd';
import type { AuditEntry } from '../api/types';
import { auditLabel } from '../utils/labels';
import { fmtDateTime } from '../utils/format';

export default function HistoryTimeline({ items }: { items?: AuditEntry[] }) {
  if (!items || items.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có lịch sử thay đổi" />;
  return (
    <Timeline
      items={items.map((h) => ({
        color: h.action === 'CANCEL' ? 'red' : h.action === 'CREATE' || h.action === 'IMPORT' || h.action === 'SUBMIT' ? 'green' : 'blue',
        children: (
          <div>
            <b>{auditLabel[h.action] ?? h.action}</b>
            <Typography.Text type="secondary"> · {h.user?.fullName ?? 'Hệ thống'} · {fmtDateTime(h.createdAt)}</Typography.Text>
            {h.reason && <div>Lý do: {h.reason}</div>}
            {h.detail && <div className="muted small">{h.detail}</div>}
          </div>
        ),
      }))}
    />
  );
}
