import { Form, Input } from 'antd';
import { ui } from '../utils/globals';

/** Hộp thoại bắt buộc nhập lý do (dùng cho sửa/hủy chứng từ). Trả về lý do hoặc null nếu hủy. */
export function askReason(opts: { title: string; content?: string; okText?: string; danger?: boolean; label?: string; required?: boolean }): Promise<string | null> {
  return new Promise((resolve) => {
    let value = '';
    let done = false;
    const m = ui.modal.confirm({
      title: opts.title,
      icon: null,
      width: 520,
      okText: opts.okText ?? 'Xác nhận',
      cancelText: 'Đóng',
      okButtonProps: { danger: opts.danger },
      content: (
        <Form layout="vertical" style={{ marginTop: 12 }}>
          {opts.content && <p style={{ marginTop: 0 }}>{opts.content}</p>}
          <Form.Item label={opts.label ?? 'Lý do'} required={opts.required !== false} style={{ marginBottom: 0 }}>
            <Input.TextArea autoFocus rows={3} maxLength={500} showCount placeholder="Nhập lý do..."
              onChange={(e) => { value = e.target.value; }} />
          </Form.Item>
        </Form>
      ),
      onOk: () => {
        if (opts.required !== false && !value.trim()) {
          ui.message.warning('Vui lòng nhập lý do');
          return Promise.reject();
        }
        done = true;
        resolve(value.trim());
        return Promise.resolve();
      },
      afterClose: () => { if (!done) resolve(null); },
    });
    void m;
  });
}
