import { Button, Space, Tooltip } from 'antd';
import { FileExcelOutlined, FileImageOutlined, FilePdfOutlined, FileOutlined } from '@ant-design/icons';
import type { FileInfo } from '../api/types';
import { openFile } from '../api/client';
import { fmtSize } from '../utils/format';

function icon(name: string) {
  const n = name.toLowerCase();
  if (n.endsWith('.pdf')) return <FilePdfOutlined style={{ color: '#c4320a' }} />;
  if (/\.(png|jpe?g|webp)$/.test(n)) return <FileImageOutlined style={{ color: '#256abf' }} />;
  if (/\.(xlsx?|csv)$/.test(n)) return <FileExcelOutlined style={{ color: '#008300' }} />;
  return <FileOutlined />;
}

export default function FileLink({ file, label }: { file?: FileInfo | null; label?: string }) {
  if (!file) return <span className="muted">—</span>;
  return (
    <Tooltip title={`${file.name} · ${fmtSize(file.size)} – bấm để xem/tải`}>
      <Button type="link" size="small" style={{ padding: 0, height: 'auto' }} onClick={() => openFile(file.id, file.name, file.contentType)}>
        <Space size={4}>{icon(file.name)}<span className="file-name">{label ?? file.name}</span></Space>
      </Button>
    </Tooltip>
  );
}
