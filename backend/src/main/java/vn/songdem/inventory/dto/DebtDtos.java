package vn.songdem.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.songdem.inventory.dto.CommonDtos.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class DebtDtos {
    private DebtDtos() {}

    public record Allocation(@NotBlank String receiptId,
                             @NotNull(message = "Vui lòng nhập số tiền") @DecimalMin(value = "0", inclusive = false, message = "Số tiền thanh toán phải lớn hơn 0") BigDecimal amount) {}

    public record PaymentRequest(@NotEmpty(message = "Vui lòng chọn phiếu nhập cần thanh toán") List<@Valid Allocation> allocations,
                                 @NotNull(message = "Vui lòng chọn ngày thanh toán") LocalDate paidAt,
                                 @NotBlank(message = "Vui lòng chọn hình thức thanh toán") String method,
                                 @Size(max = 500) String note) {}

    public record PaymentUpdateRequest(@NotNull(message = "Vui lòng nhập số tiền") @DecimalMin(value = "0", inclusive = false, message = "Số tiền thanh toán phải lớn hơn 0") BigDecimal amount,
                                       @NotNull(message = "Vui lòng chọn ngày thanh toán") LocalDate paidAt,
                                       @NotBlank(message = "Vui lòng chọn hình thức thanh toán") String method,
                                       @Size(max = 500) String note,
                                       @NotBlank(message = "Vui lòng nhập lý do chỉnh sửa") String reason) {}

    public record SupplierDebtDto(RefDto supplier, String contactName, String phone, String paymentMethod,
                                  int receiptCount, int openReceiptCount, BigDecimal totalAmount, BigDecimal paidAmount,
                                  BigDecimal remainingAmount, BigDecimal overdueAmount, LocalDate oldestOpenDate) {}

    public record DebtReconLineDto(String id, int lineNo, String invoiceNo, LocalDate invoiceDate, String receiptId,
                                   String receiptCode, BigDecimal fileAmount, BigDecimal filePaid, BigDecimal systemAmount,
                                   BigDecimal systemPaid, String result, String note) {}

    public record DebtReconUploadDto(String id, FileDto invoiceFile, FileDto evidenceFile, UserRef uploadedBy, LocalDateTime uploadedAt) {}

    public record DebtReconDto(String id, String code, RefDto supplier, LocalDate periodFrom, LocalDate periodTo, String status,
                               BigDecimal fileTotal, BigDecimal systemTotal, BigDecimal systemPaid, int matchedCount,
                               int mismatchCount, int unresolvedCount, String note, UserRef createdBy, UserRef completedBy,
                               LocalDateTime completedAt, LocalDateTime createdAt, FileDto invoiceFile, FileDto evidenceFile,
                               List<DebtReconLineDto> lines, List<DebtReconUploadDto> uploads, List<AuditDto> history) {}

    public record LineNoteRequest(@Size(max = 500) String note) {}

    public record CompleteRequest(@Size(max = 1000) String note) {}
}
