import { Badge, Button, Empty, List, Popover, Space, Typography } from 'antd';
import {
  BellOutlined, WarningOutlined, FileSearchOutlined, SyncOutlined, DollarOutlined, ExclamationCircleOutlined, InfoCircleOutlined,
} from '@ant-design/icons';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/vi';
import { api } from '../api/client';
import type { Notification } from '../api/types';

dayjs.extend(relativeTime);
dayjs.locale('vi');

const ICONS: Record<string, JSX.Element> = {
  LOW_STOCK: <WarningOutlined style={{ color: '#c98500' }} />,
  OVER_SOLD: <ExclamationCircleOutlined style={{ color: '#c4320a' }} />,
  STOCKTAKE: <FileSearchOutlined style={{ color: '#256abf' }} />,
  RECOUNT: <SyncOutlined style={{ color: '#256abf' }} />,
  DEBT: <DollarOutlined style={{ color: '#6250d6' }} />,
};

export default function NotificationBell() {
  const [items, setItems] = useState<Notification[]>([]);
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const nav = useNavigate();

  const load = useCallback(async () => {
    try {
      const { data } = await api.get<{ items: Notification[]; unread: number }>('/notifications', { params: { limit: 30 } });
      setItems(data.items);
      setUnread(data.unread);
    } catch { /* bỏ qua */ }
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 60000);
    return () => clearInterval(t);
  }, [load]);

  const click = async (n: Notification) => {
    setOpen(false);
    if (!n.read) {
      await api.post(`/notifications/${n.id}/read`).catch(() => undefined);
      load();
    }
    if (n.link) nav(n.link);
  };

  const readAll = async () => {
    await api.post('/notifications/read-all').catch(() => undefined);
    load();
  };

  const content = (
    <div className="notif-panel">
      <div className="notif-head">
        <b>Thông báo</b>
        <Button type="link" size="small" disabled={!unread} onClick={readAll}>Đánh dấu đã đọc tất cả</Button>
      </div>
      {items.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Chưa có thông báo" /> : (
        <List
          dataSource={items}
          renderItem={(n) => (
            <List.Item className={`notif-item ${n.read ? '' : 'unread'}`} onClick={() => click(n)}>
              <Space align="start">
                <span className="notif-icon">{ICONS[n.type] ?? <InfoCircleOutlined />}</span>
                <div>
                  <div className="notif-title">{n.title}</div>
                  {n.message && <Typography.Paragraph className="notif-msg" ellipsis={{ rows: 2 }}>{n.message}</Typography.Paragraph>}
                  <div className="notif-time">{dayjs(n.createdAt).fromNow()}</div>
                </div>
              </Space>
            </List.Item>
          )}
        />
      )}
    </div>
  );

  return (
    <Popover content={content} trigger="click" open={open} onOpenChange={(o) => { setOpen(o); if (o) load(); }} placement="bottomRight" arrow={false}>
      <Badge count={unread} size="small" offset={[-4, 4]}>
        <Button type="text" shape="circle" icon={<BellOutlined style={{ fontSize: 18 }} />} aria-label={`Thông báo (${unread} chưa đọc)`} />
      </Badge>
    </Popover>
  );
}
