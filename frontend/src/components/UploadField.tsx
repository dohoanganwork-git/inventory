import { Upload, Button } from 'antd';
import { InboxOutlined, UploadOutlined } from '@ant-design/icons';
import type { UploadFile } from 'antd';

interface Props {
  value?: File | null; onChange?: (f: File | null) => void; accept: string; hint?: string; dragger?: boolean; label?: string;
}

/** Ô chọn một file (không tự tải lên – file được gửi cùng biểu mẫu). */
export default function UploadField({ value, onChange, accept, hint, dragger = true, label = 'Chọn file' }: Props) {
  const fileList: UploadFile[] = value ? [{ uid: '1', name: value.name, status: 'done', size: value.size }] : [];
  const props = {
    accept, maxCount: 1, fileList,
    beforeUpload: (f: File) => { onChange?.(f); return false; },
    onRemove: () => { onChange?.(null); },
  };
  if (!dragger) return <Upload {...props}><Button icon={<UploadOutlined />}>{label}</Button></Upload>;
  return (
    <div className="upload-box">
      <Upload.Dragger {...props}>
        <p className="ant-upload-drag-icon" style={{ marginBottom: 4 }}><InboxOutlined /></p>
        <p className="ant-upload-text" style={{ fontSize: 14 }}>Kéo thả file vào đây hoặc bấm để chọn</p>
        {hint && <p className="ant-upload-hint" style={{ fontSize: 12 }}>{hint}</p>}
      </Upload.Dragger>
    </div>
  );
}
