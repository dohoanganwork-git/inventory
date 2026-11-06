import { Card } from 'antd';
import type { ReactNode } from 'react';

interface Props {
  title: string; value: ReactNode; icon?: ReactNode; hint?: ReactNode;
  tone?: 'default' | 'danger' | 'warning' | 'success' | 'info'; onClick?: () => void;
}

export default function StatCard({ title, value, icon, hint, tone = 'default', onClick }: Props) {
  return (
    <Card className={`stat-card tone-${tone} ${onClick ? 'clickable' : ''}`} onClick={onClick} variant="borderless">
      <div className="stat-top">
        <span className="stat-title">{title}</span>
        {icon && <span className="stat-icon">{icon}</span>}
      </div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </Card>
  );
}
