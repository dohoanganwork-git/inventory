import { Button, Space, Typography } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

interface Props { title: ReactNode; subtitle?: ReactNode; extra?: ReactNode; back?: string | boolean; tags?: ReactNode }

export default function PageHeader({ title, subtitle, extra, back, tags }: Props) {
  const nav = useNavigate();
  return (
    <div className="page-header">
      <div className="page-header-main">
        {back && (
          <Button type="text" icon={<ArrowLeftOutlined />} aria-label="Quay lại"
            onClick={() => (typeof back === 'string' ? nav(back) : nav(-1))} />
        )}
        <div>
          <Space wrap size={8} align="center">
            <Typography.Title level={3} style={{ margin: 0 }}>{title}</Typography.Title>
            {tags}
          </Space>
          {subtitle && <div className="page-subtitle">{subtitle}</div>}
        </div>
      </div>
      {extra && <div className="page-header-extra"><Space wrap>{extra}</Space></div>}
    </div>
  );
}
