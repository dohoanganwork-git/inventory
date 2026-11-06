package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.domain.Enums.DocStatus;
import vn.songdem.inventory.dto.CommonDtos.RefDto;
import vn.songdem.inventory.dto.DebtDtos.*;
import vn.songdem.inventory.dto.StockDtos.PaymentDto;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** UC_14, UC_15, UC_16 – Theo dõi và thanh toán công nợ. */
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final DebtReconciliationRepository debtReconRepository;
    private final ReceiptService receiptService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    /** Tổng hợp công nợ theo nhà cung cấp. */
    @Transactional(readOnly = true)
    public List<SupplierDebtDto> summary() {
        Map<String, List<GoodsReceipt>> bySupplier = new LinkedHashMap<>();
        for (GoodsReceipt r : receiptRepository.findAllActiveWithSupplier()) {
            bySupplier.computeIfAbsent(r.getSupplier().getId(), k -> new ArrayList<>()).add(r);
        }
        LocalDate today = LocalDate.now();
        List<SupplierDebtDto> out = new ArrayList<>();
        for (List<GoodsReceipt> list : bySupplier.values()) {
            Supplier s = list.get(0).getSupplier();
            BigDecimal total = BigDecimal.ZERO, paid = BigDecimal.ZERO, remain = BigDecimal.ZERO, overdue = BigDecimal.ZERO;
            int open = 0;
            LocalDate oldest = null;
            for (GoodsReceipt r : list) {
                total = total.add(r.getTotalAmount());
                paid = paid.add(r.getPaidAmount());
                remain = remain.add(r.getRemainingAmount());
                if (r.getRemainingAmount().signum() > 0) {
                    open++;
                    if (oldest == null || r.getReceiptDate().isBefore(oldest)) oldest = r.getReceiptDate();
                    if (r.getDueDate() != null && r.getDueDate().isBefore(today)) overdue = overdue.add(r.getRemainingAmount());
                }
            }
            out.add(new SupplierDebtDto(RefDto.of(s), s.getContactName(), s.getPhone(), s.getPaymentMethod(), list.size(), open,
                    total, paid, remain, overdue, oldest));
        }
        out.sort(Comparator.comparing(SupplierDebtDto::remainingAmount).reversed());
        return out;
    }

    private void ensureNotLocked(GoodsReceipt r, LocalDate date) {
        if (debtReconRepository.isDateLocked(r.getSupplier().getId(), date)) {
            throw ApiException.conflict("PERIOD_LOCKED", "Ngày " + SalesService.fmt(date)
                    + " thuộc kỳ đối soát công nợ đã hoàn tất của " + r.getSupplier().getName() + ". Vui lòng mở lại kỳ đối soát trước.");
        }
    }

    /** UC_15 – Thanh toán công nợ cho một hoặc nhiều phiếu nhập của cùng nhà cung cấp. */
    @Transactional
    public List<PaymentDto> create(PaymentRequest req, MultipartFile evidence, User actor) {
        if (req.paidAt().isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày thanh toán không được sau ngày hôm nay");
        fileStorageService.validate(evidence, FileStorageService.EVIDENCE_EXT, "Minh chứng thanh toán");
        String supplierId = null;
        Set<String> seen = new HashSet<>();
        List<GoodsReceipt> receipts = new ArrayList<>();
        for (Allocation a : req.allocations()) {
            if (!seen.add(a.receiptId())) throw ApiException.badRequest("Phiếu nhập bị chọn trùng");
            GoodsReceipt r = receiptService.find(a.receiptId());
            if (r.getStatus() != DocStatus.ACTIVE) throw ApiException.badRequest("Phiếu " + r.getCode() + " đã bị hủy");
            if (supplierId == null) supplierId = r.getSupplier().getId();
            else if (!supplierId.equals(r.getSupplier().getId())) throw ApiException.badRequest("Chỉ được thanh toán gộp các phiếu của cùng một nhà cung cấp");
            if (a.amount() == null || a.amount().signum() <= 0) throw ApiException.badRequest("Số tiền thanh toán phải lớn hơn 0");
            if (a.amount().compareTo(r.getRemainingAmount()) > 0) {
                throw ApiException.badRequest("Số tiền thanh toán cho phiếu " + r.getCode() + " (" + String.format("%,.0f", a.amount())
                        + " đ) lớn hơn số còn nợ (" + String.format("%,.0f", r.getRemainingAmount()) + " đ)");
            }
            ensureNotLocked(r, req.paidAt());
            receipts.add(r);
        }
        StoredFile file = fileStorageService.store(evidence, FileStorageService.EVIDENCE_EXT, "Minh chứng thanh toán", "EVIDENCE", actor);
        List<PaymentDto> out = new ArrayList<>();
        for (int i = 0; i < receipts.size(); i++) {
            GoodsReceipt r = receipts.get(i);
            Payment p = new Payment();
            p.setCode(CodeGenerator.next("TT", req.paidAt(), paymentRepository::countByCodeStartingWith));
            p.setReceipt(r);
            p.setAmount(req.allocations().get(i).amount());
            p.setPaidAt(req.paidAt());
            p.setMethod(req.method().trim());
            p.setNote(TextUtil.trimToNull(req.note()));
            p.setEvidenceFile(file);
            p.setCreatedBy(actor);
            paymentRepository.saveAndFlush(p);
            receiptService.recomputeDebt(r);
            auditService.log("PAYMENT", p.getId(), p.getCode(), "CREATE", null,
                    "Thanh toán " + String.format("%,.0f", p.getAmount()) + " đ cho phiếu " + r.getCode(), actor);
            out.add(receiptService.paymentDto(p));
        }
        return out;
    }

    private Payment find(String id) {
        return paymentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khoản thanh toán"));
    }

    /** UC_16 – Chỉnh sửa khoản thanh toán. */
    @Transactional
    public PaymentDto update(String id, PaymentUpdateRequest req, MultipartFile evidence, User actor) {
        Payment p = find(id);
        if (p.getStatus() == DocStatus.CANCELLED) throw ApiException.badRequest("Khoản thanh toán đã bị hủy");
        GoodsReceipt r = p.getReceipt();
        ensureNotLocked(r, p.getPaidAt());
        ensureNotLocked(r, req.paidAt());
        if (req.paidAt().isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày thanh toán không được sau ngày hôm nay");
        BigDecimal maxAllowed = r.getRemainingAmount().add(p.getAmount());
        if (req.amount().compareTo(maxAllowed) > 0) {
            throw ApiException.badRequest("Số tiền mới (" + String.format("%,.0f", req.amount()) + " đ) lớn hơn tổng nợ còn lại của phiếu ("
                    + String.format("%,.0f", maxAllowed) + " đ)");
        }
        String detail = "Số tiền: " + String.format("%,.0f", p.getAmount()) + " → " + String.format("%,.0f", req.amount())
                + "; Ngày: " + SalesService.fmt(p.getPaidAt()) + " → " + SalesService.fmt(req.paidAt())
                + "; Hình thức: " + p.getMethod() + " → " + req.method();
        p.setAmount(req.amount());
        p.setPaidAt(req.paidAt());
        p.setMethod(req.method().trim());
        p.setNote(TextUtil.trimToNull(req.note()));
        if (evidence != null && !evidence.isEmpty()) {
            p.setEvidenceFile(fileStorageService.store(evidence, FileStorageService.EVIDENCE_EXT, "Minh chứng thanh toán", "EVIDENCE", actor));
            detail += "; Thay minh chứng";
        }
        paymentRepository.flush();
        receiptService.recomputeDebt(r);
        auditService.log("PAYMENT", p.getId(), p.getCode(), "UPDATE", req.reason().trim(), detail, actor);
        return receiptService.paymentDto(p);
    }

    /** UC_16 – Hủy khoản thanh toán. */
    @Transactional
    public PaymentDto cancel(String id, String reason, User actor) {
        if (TextUtil.isBlank(reason)) throw ApiException.badRequest("Vui lòng nhập lý do hủy");
        Payment p = find(id);
        if (p.getStatus() == DocStatus.CANCELLED) throw ApiException.badRequest("Khoản thanh toán đã bị hủy");
        ensureNotLocked(p.getReceipt(), p.getPaidAt());
        p.setStatus(DocStatus.CANCELLED);
        p.setCancelReason(reason.trim());
        paymentRepository.flush();
        receiptService.recomputeDebt(p.getReceipt());
        auditService.log("PAYMENT", p.getId(), p.getCode(), "CANCEL", reason.trim(),
                "Hủy khoản " + String.format("%,.0f", p.getAmount()) + " đ", actor);
        return receiptService.paymentDto(p);
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> list(LocalDate from, LocalDate to) {
        return paymentRepository.findActiveInPeriod(from == null ? LocalDate.of(2000, 1, 1) : from, to == null ? LocalDate.now() : to)
                .stream().map(receiptService::paymentDto).toList();
    }
}
