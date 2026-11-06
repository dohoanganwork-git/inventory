export const roleLabel: Record<string, string> = { MANAGER: 'Quản lý', STAFF: 'Nhân viên', ACCOUNTANT: 'Kế toán' };
export const roleColor: Record<string, string> = { MANAGER: 'geekblue', STAFF: 'cyan', ACCOUNTANT: 'purple' };

export const movementLabel: Record<string, string> = {
  RECEIPT: 'Nhập hàng', RECEIPT_EDIT: 'Sửa phiếu nhập', RECEIPT_CANCEL: 'Hủy phiếu nhập',
  SALE: 'Xuất bán (POS)', SALE_EDIT: 'Sửa phiếu xuất', SALE_CANCEL: 'Hủy phiếu xuất',
  ADJUSTMENT: 'Điều chỉnh kiểm kê', DAMAGE: 'Hỏng / hủy',
};

export const auditLabel: Record<string, string> = {
  CREATE: 'Tạo mới', UPDATE: 'Chỉnh sửa', CANCEL: 'Hủy', IMPORT: 'Nhập file', ACTIVATE: 'Kích hoạt',
  DEACTIVATE: 'Ngừng sử dụng', THRESHOLD: 'Đổi mốc cảnh báo', SUBMIT: 'Gửi file kiểm kê', REPLACE_FILE: 'Thay file kiểm kê',
  RECOUNT_SUBMIT: 'Gửi file kiểm kê lại', APPROVE_ADJUST: 'Đồng ý điều chỉnh tồn kho', REQUEST_RECOUNT: 'Yêu cầu kiểm kê lại',
  REUPLOAD: 'Tải lại file', REMATCH: 'Đối chiếu lại', COMPLETE: 'Hoàn tất đối soát', REOPEN: 'Mở lại kỳ đối soát',
};

export const reconResultLabel: Record<string, string> = {
  MATCHED: 'Khớp', AMOUNT_MISMATCH: 'Lệch tổng tiền', PAID_MISMATCH: 'Lệch số đã trả',
  NOT_IN_SYSTEM: 'Chưa có phiếu nhập', NOT_IN_FILE: 'Không có trong file',
};

export const submissionLabel: Record<string, string> = { INITIAL: 'Gửi lần đầu', REPLACE: 'Thay file', RECOUNT: 'Kiểm kê lại' };

export const PAYMENT_METHODS = ['Chuyển khoản', 'Tiền mặt', 'Ví điện tử', 'Khác'];
