package vn.songdem.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.songdem.inventory.dto.CommonDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StockDtos {
    private StockDtos() {}

    public record ReceiptLineRequest(
            @NotBlank(message = "Vui lòng chọn mặt hàng") String itemId,
            @NotNull(message = "Vui lòng nhập số lượng") @Positive(message = "Số lượng phải lớn hơn 0") Integer quantity,
            @NotNull(message = "Vui lòng nhập đơn giá") @DecimalMin(value = "0", inclusive = false, message = "Đơn giá phải lớn hơn 0") BigDecimal unitPrice) {}

    public record ReceiptRequest(
            @NotBlank(message = "Vui lòng chọn nhà cung cấp") String supplierId,
            @NotBlank(message = "Vui lòng nhập số hóa đơn") @Size(max = 50, message = "Số hóa đơn tối đa 50 ký tự") String invoiceNo,
            @NotNull(message = "Vui lòng chọn ngày nhập") LocalDate receiptDate,
            LocalDate dueDate,
            @Size(max = 500) String note,
            @NotEmpty(message = "Phiếu nhập phải có ít nhất một mặt hàng") List<@Valid ReceiptLineRequest> lines,
            String reason) {}

    public record ReceiptLineDto(String id, RefDto item, String unit, int quantity, BigDecimal unitPrice, BigDecimal lineAmount) {}

    public record PaymentDto(String id, String code, String receiptId, String receiptCode, String invoiceNo, RefDto supplier,
                             BigDecimal amount, LocalDate paidAt, String method, FileDto evidenceFile, String status,
                             String note, String cancelReason, UserRef createdBy, LocalDateTime createdAt, boolean locked) {}

    public record ReceiptDto(String id, String code, RefDto supplier, String invoiceNo, LocalDate receiptDate, LocalDate dueDate,
                             BigDecimal totalAmount, BigDecimal paidAmount, BigDecimal remainingAmount,
                             String debtStatus, String status, String reconcileStatus, String reconcileNote,
                             int lineCount, int totalQuantity, String note, String cancelReason,
                             UserRef createdBy, UserRef cancelledBy, LocalDateTime createdAt, FileDto invoiceFile,
                             boolean overdue,
                             List<ReceiptLineDto> lines, List<PaymentDto> payments, List<AuditDto> history) {}

    public record SalesLineDto(String id, RefDto item, String unit, int quantity, int deductedQuantity, BigDecimal unitPrice,
                               BigDecimal amount, int stockBefore, int stockAfter, int overQuantity) {}

    public record SalesDto(String id, String code, LocalDate salesDate, int totalQuantity, BigDecimal totalAmount,
                           int overQuantity, String status, int lineCount, String cancelReason,
                           UserRef importedBy, UserRef cancelledBy, LocalDateTime importedAt, FileDto originalFile,
                           List<SalesLineDto> lines, List<AuditDto> history) {}

    public record SalesPreviewRow(int rowNumber, String posCode, String fileName, String itemId, String itemCode,
                                  String itemName, String unit, int quantity, BigDecimal unitPrice, int currentStock,
                                  int stockAfter, int overQuantity, boolean lowStockAfter) {}

    public record SalesPreview(LocalDate salesDate, String fileName, List<SalesPreviewRow> rows, List<String> errors,
                               int totalQuantity, BigDecimal totalAmount, int overItems, int lowStockItems, boolean canImport) {}

    public record SalesLineUpdate(@NotBlank String lineId,
                                  @NotNull(message = "Vui lòng nhập số lượng") @PositiveOrZero(message = "Số lượng không được âm") Integer quantity) {}

    public record SalesUpdateRequest(@NotEmpty List<@Valid SalesLineUpdate> lines,
                                     @NotBlank(message = "Vui lòng nhập lý do chỉnh sửa") String reason) {}
}
