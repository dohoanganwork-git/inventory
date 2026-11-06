package vn.songdem.inventory.domain;

/** Các kiểu liệt kê dùng chung trong hệ thống. */
public final class Enums {
    private Enums() {}

    /** Trạng thái chứng từ (phiếu nhập, phiếu xuất, thanh toán). */
    public enum DocStatus { ACTIVE, CANCELLED }

    /** Trạng thái công nợ của phiếu nhập. */
    public enum DebtStatus { UNPAID, PARTIAL, PAID }

    /** Trạng thái đối soát công nợ của phiếu nhập. */
    public enum ReconcileStatus { NONE, MATCHED, MISMATCHED }

    /** Trạng thái kỳ đối soát công nợ. */
    public enum DebtReconStatus { IN_PROGRESS, COMPLETED }

    /** Kết quả đối chiếu một dòng hóa đơn. */
    public enum DebtReconResult { MATCHED, AMOUNT_MISMATCH, PAID_MISMATCH, NOT_IN_SYSTEM, NOT_IN_FILE }

    /** Trạng thái đợt kiểm kê và đối soát tồn kho. */
    public enum StocktakeStatus { PENDING_REVIEW, RECOUNT_REQUESTED, COMPLETED }

    /** Quyết định của Quản lý cho từng dòng kiểm kê. */
    public enum LineDecision { NONE, APPROVED, RECOUNT }

    /** Loại biến động tồn kho. */
    public enum MovementType {
        RECEIPT, RECEIPT_EDIT, RECEIPT_CANCEL,
        SALE, SALE_EDIT, SALE_CANCEL,
        ADJUSTMENT, DAMAGE
    }

    /** Loại thông báo. */
    public enum NotificationType { LOW_STOCK, OVER_SOLD, STOCKTAKE, RECOUNT, DEBT, SYSTEM }
}
