export type Role = 'MANAGER' | 'STAFF' | 'ACCOUNTANT';

export interface Ref { id: string; code: string; name: string }
export interface UserRef { id: string; fullName: string; role: string }
export interface FileInfo { id: string; name: string; contentType: string; size: number; uploadedAt: string }
export interface AuditEntry { id: string; action: string; reason?: string; detail?: string; user?: UserRef; createdAt: string }

export interface User {
  id: string; username: string; fullName: string; email?: string; phone?: string;
  role: Role; roleLabel: string; active: boolean; createdAt: string;
}

export interface Category { id: string; code: string; name: string; description?: string; active: boolean; itemCount: number; createdAt: string }

export interface Supplier {
  id: string; code: string; name: string; contactName?: string; phone?: string; email?: string; address?: string;
  taxCode?: string; paymentMethod?: string; bankAccount?: string; contractNo?: string; contractStart?: string;
  contractEnd?: string; contractNote?: string; contractStatus: 'ACTIVE' | 'EXPIRING' | 'EXPIRED' | 'NONE';
  contractFile?: FileInfo; active: boolean; itemCount: number; openDebt?: number; createdAt: string;
}

export interface Item {
  id: string; code: string; posCode: string; name: string; unit: string; category: Ref; supplier?: Ref;
  minStock: number; maxStock?: number; currentStock: number; lastCost?: number; salePrice?: number;
  stockValue?: number; stockStatus: 'LOW' | 'OVER' | 'NORMAL'; active: boolean; createdAt: string;
}

export interface Movement {
  id: string; type: string; quantityChange: number; stockBefore: number; stockAfter: number; movementDate: string;
  refType?: string; refId?: string; refCode?: string; note?: string; createdBy?: string; createdAt: string;
}

export interface ReceiptLine { id: string; item: Ref; unit: string; quantity: number; unitPrice: number; lineAmount: number }

export interface Payment {
  id: string; code: string; receiptId: string; receiptCode: string; invoiceNo: string; supplier: Ref; amount: number;
  paidAt: string; method: string; evidenceFile?: FileInfo; status: 'ACTIVE' | 'CANCELLED'; note?: string;
  cancelReason?: string; createdBy?: UserRef; createdAt: string; locked: boolean;
}

export interface Receipt {
  id: string; code: string; supplier: Ref; invoiceNo: string; receiptDate: string; dueDate?: string; totalAmount: number;
  paidAmount?: number; remainingAmount?: number; debtStatus?: 'UNPAID' | 'PARTIAL' | 'PAID'; status: 'ACTIVE' | 'CANCELLED';
  reconcileStatus?: string; reconcileNote?: string; lineCount: number; totalQuantity: number; note?: string;
  cancelReason?: string; createdBy?: UserRef; cancelledBy?: UserRef; createdAt: string; invoiceFile?: FileInfo;
  overdue: boolean; lines?: ReceiptLine[]; payments?: Payment[]; history?: AuditEntry[];
}

export interface SalesLine {
  id: string; item: Ref; unit: string; quantity: number; deductedQuantity: number; unitPrice: number; amount: number;
  stockBefore: number; stockAfter: number; overQuantity: number;
}

export interface Sales {
  id: string; code: string; salesDate: string; totalQuantity: number; totalAmount: number; overQuantity: number;
  status: 'ACTIVE' | 'CANCELLED'; lineCount: number; cancelReason?: string; importedBy?: UserRef; cancelledBy?: UserRef;
  importedAt: string; originalFile?: FileInfo; lines?: SalesLine[]; history?: AuditEntry[];
}

export interface SalesPreviewRow {
  rowNumber: number; posCode: string; fileName?: string; itemId?: string; itemCode?: string; itemName?: string; unit?: string;
  quantity: number; unitPrice?: number; currentStock: number; stockAfter: number; overQuantity: number; lowStockAfter: boolean;
}

export interface SalesPreview {
  salesDate: string; fileName: string; rows: SalesPreviewRow[]; errors: string[]; totalQuantity: number;
  totalAmount: number; overItems: number; lowStockItems: number; canImport: boolean;
}

export interface SupplierDebt {
  supplier: Ref; contactName?: string; phone?: string; paymentMethod?: string; receiptCount: number; openReceiptCount: number;
  totalAmount: number; paidAmount: number; remainingAmount: number; overdueAmount: number; oldestOpenDate?: string;
}

export interface DebtReconLine {
  id: string; lineNo: number; invoiceNo: string; invoiceDate?: string; receiptId?: string; receiptCode?: string;
  fileAmount?: number; filePaid?: number; systemAmount?: number; systemPaid?: number; result: string; note?: string;
}

export interface DebtRecon {
  id: string; code: string; supplier: Ref; periodFrom: string; periodTo: string; status: 'IN_PROGRESS' | 'COMPLETED';
  fileTotal: number; systemTotal: number; systemPaid: number; matchedCount: number; mismatchCount: number;
  unresolvedCount: number; note?: string; createdBy?: UserRef; completedBy?: UserRef; completedAt?: string; createdAt: string;
  invoiceFile?: FileInfo; evidenceFile?: FileInfo; lines?: DebtReconLine[];
  uploads?: { id: string; invoiceFile?: FileInfo; evidenceFile?: FileInfo; uploadedBy?: UserRef; uploadedAt: string }[];
  history?: AuditEntry[];
}

export interface StocktakeLine {
  id: string; item: Ref; unit: string; bookQty: number; damagedQty: number; damageReason?: string; expectedQty: number;
  countedQty: number; varianceQty: number; initialVarianceQty: number; recountTimes: number;
  decision: 'NONE' | 'APPROVED' | 'RECOUNT'; adjustedQty?: number; needsDecision: boolean;
}

export interface Stocktake {
  id: string; code: string; stocktakeDate: string; status: 'PENDING_REVIEW' | 'RECOUNT_REQUESTED' | 'COMPLETED'; round: number;
  totalLines: number; matchedLines: number; varianceLines: number; pendingLines: number; recountLines: number;
  accuracy: number; damagedQty: number; decisionReason?: string; recountReason?: string; createdBy?: UserRef;
  reviewedBy?: UserRef; createdAt: string; decidedAt?: string; countFile?: FileInfo; damageFile?: FileInfo;
  lines?: StocktakeLine[];
  submissions?: { id: string; kind: string; countFile?: FileInfo; damageFile?: FileInfo; submittedBy?: UserRef; varianceLines: number; submittedAt: string }[];
  history?: AuditEntry[];
}

export interface Notification { id: string; type: string; title: string; message?: string; link?: string; read: boolean; createdAt: string }

export interface Alerts {
  lowStock: Item[]; overStock: Item[]; outOfStock: number;
  overSold: { salesId: string; salesCode: string; salesDate: string; item: Ref; unit: string; quantity: number; stockBefore: number; overQuantity: number }[];
}

export interface ReportColumn { key: string; title: string; type: 'text' | 'int' | 'money' | 'percent' | 'date' }
export interface ReportTable {
  key: string; title: string; subtitle: string; columns: ReportColumn[]; rows: Record<string, unknown>[];
  totals: Record<string, unknown>; extra: Record<string, unknown>;
}

export interface Dashboard {
  itemCount: number; lowStockCount: number; outOfStockCount: number; overStockCount: number; stockValue?: number;
  openDebt?: number; overdueDebt?: number; openDebtReceipts: number; pendingStocktakes: number; recountStocktakes: number;
  openDebtRecons: number; latestSalesDate?: string; salesUpToDate: boolean;
  sales14: { date: string; quantity: number; revenue?: number }[];
  topItems: { code: string; name: string; unit: string; quantity: number; revenue?: number }[];
  lowStockItems: Item[];
}
