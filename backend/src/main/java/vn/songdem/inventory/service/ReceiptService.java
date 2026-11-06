package vn.songdem.inventory.service;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.domain.Enums.*;
import vn.songdem.inventory.dto.CommonDtos.*;
import vn.songdem.inventory.dto.StockDtos.*;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** UC_10, UC_12, UC_13, UC_14 – Phiếu nhập hàng và công nợ phát sinh. */
@Service
@RequiredArgsConstructor
public class ReceiptService {
    private final GoodsReceiptRepository receiptRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final PaymentRepository paymentRepository;
    private final DebtReconciliationRepository debtReconRepository;
    private final InventoryService inventoryService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public record ReceiptFilter(LocalDate from, LocalDate to, String supplierId, String itemId, String status,
                                String debtStatus, String q, Boolean openDebtOnly) {}

    // ---------- Truy vấn ----------

    @Transactional(readOnly = true)
    public List<ReceiptDto> list(ReceiptFilter f, boolean withDebt) {
        Specification<GoodsReceipt> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (f.from() != null) ps.add(cb.greaterThanOrEqualTo(root.get("receiptDate"), f.from()));
            if (f.to() != null) ps.add(cb.lessThanOrEqualTo(root.get("receiptDate"), f.to()));
            if (f.supplierId() != null && !f.supplierId().isBlank()) ps.add(cb.equal(root.get("supplier").get("id"), f.supplierId()));
            if (f.status() != null && !f.status().isBlank()) ps.add(cb.equal(root.get("status"), DocStatus.valueOf(f.status())));
            if (f.debtStatus() != null && !f.debtStatus().isBlank()) {
                ps.add(cb.equal(root.get("debtStatus"), DebtStatus.valueOf(f.debtStatus())));
                ps.add(cb.equal(root.get("status"), DocStatus.ACTIVE));
            }
            if (Boolean.TRUE.equals(f.openDebtOnly())) {
                ps.add(cb.greaterThan(root.get("remainingAmount"), BigDecimal.ZERO));
                ps.add(cb.equal(root.get("status"), DocStatus.ACTIVE));
            }
            if (f.itemId() != null && !f.itemId().isBlank()) {
                var sub = query.subquery(String.class);
                var l = sub.from(GoodsReceiptLine.class);
                sub.select(l.get("receipt").get("id")).where(cb.equal(l.get("item").get("id"), f.itemId()));
                ps.add(root.get("id").in(sub));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
                Join<GoodsReceipt, Supplier> s = root.join("supplier");
                ps.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("invoiceNo")), like),
                        cb.like(cb.lower(s.get("name")), like)));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return receiptRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "receiptDate", "createdAt")).stream()
                .map(r -> toDto(r, withDebt, false)).toList();
    }

    @Transactional(readOnly = true)
    public ReceiptDto get(String id, boolean withDebt) {
        return toDto(find(id), withDebt, true);
    }

    public GoodsReceipt find(String id) {
        return receiptRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
    }

    public ReceiptDto toDto(GoodsReceipt r, boolean withDebt, boolean detail) {
        List<ReceiptLineDto> lines = null;
        List<PaymentDto> payments = null;
        List<AuditDto> history = null;
        if (detail) {
            lines = r.getLines().stream().map(l -> new ReceiptLineDto(l.getId(), RefDto.of(l.getItem()), l.getItem().getUnit(),
                    l.getQuantity(), l.getUnitPrice(), l.getLineAmount())).toList();
            if (withDebt) {
                payments = paymentRepository.findByReceiptIdOrderByCreatedAtDesc(r.getId()).stream().map(this::paymentDto).toList();
            }
            Set<String> ids = new HashSet<>();
            ids.add(r.getId());
            if (payments != null) payments.forEach(p -> ids.add(p.id()));
            history = auditService.history(ids);
        }
        int totalQty = r.getLines().stream().mapToInt(GoodsReceiptLine::getQuantity).sum();
        boolean overdue = withDebt && r.getStatus() == DocStatus.ACTIVE && r.getDueDate() != null
                && r.getRemainingAmount().signum() > 0 && r.getDueDate().isBefore(LocalDate.now());
        return new ReceiptDto(r.getId(), r.getCode(), RefDto.of(r.getSupplier()), r.getInvoiceNo(), r.getReceiptDate(), r.getDueDate(),
                r.getTotalAmount(), withDebt ? r.getPaidAmount() : null, withDebt ? r.getRemainingAmount() : null,
                withDebt ? r.getDebtStatus().name() : null, r.getStatus().name(),
                withDebt ? r.getReconcileStatus().name() : null, withDebt ? r.getReconcileNote() : null,
                r.getLines().size(), totalQty, r.getNote(), r.getCancelReason(),
                UserRef.of(r.getCreatedBy()), UserRef.of(r.getCancelledBy()), r.getCreatedAt(), FileDto.of(r.getInvoiceFile()),
                overdue, lines, payments, history);
    }

    public PaymentDto paymentDto(Payment p) {
        GoodsReceipt r = p.getReceipt();
        boolean locked = debtReconRepository.isDateLocked(r.getSupplier().getId(), p.getPaidAt());
        return new PaymentDto(p.getId(), p.getCode(), r.getId(), r.getCode(), r.getInvoiceNo(), RefDto.of(r.getSupplier()),
                p.getAmount(), p.getPaidAt(), p.getMethod(), FileDto.of(p.getEvidenceFile()), p.getStatus().name(),
                p.getNote(), p.getCancelReason(), UserRef.of(p.getCreatedBy()), p.getCreatedAt(), locked);
    }

    /** Kiểm tra trùng số hóa đơn của nhà cung cấp (dùng cảnh báo sớm trên giao diện). */
    @Transactional(readOnly = true)
    public Optional<String> findDuplicateInvoice(String supplierId, String invoiceNo, String excludeId) {
        if (supplierId == null || TextUtil.isBlank(invoiceNo)) return Optional.empty();
        return receiptRepository.findFirstBySupplierIdAndInvoiceNoIgnoreCaseAndStatus(supplierId, invoiceNo.trim(), DocStatus.ACTIVE)
                .filter(r -> excludeId == null || !r.getId().equals(excludeId))
                .map(GoodsReceipt::getCode);
    }

    // ---------- Tạo phiếu nhập (UC_10) ----------

    private record ParsedLine(Item item, int quantity, BigDecimal unitPrice) {}

    private List<ParsedLine> parseLines(List<ReceiptLineRequest> reqs, Map<String, Integer> existingQty) {
        List<ParsedLine> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ReceiptLineRequest l : reqs) {
            Item it = itemRepository.findById(l.itemId()).orElseThrow(() -> ApiException.badRequest("Mặt hàng không tồn tại"));
            if (!seen.add(it.getId())) throw ApiException.badRequest("Mặt hàng \"" + it.getName() + "\" bị lặp trong phiếu, vui lòng gộp số lượng");
            if (!it.isActive() && !existingQty.containsKey(it.getId())) {
                throw ApiException.badRequest("Mặt hàng \"" + it.getName() + "\" đã ngừng sử dụng, không thể chọn");
            }
            if (l.quantity() == null || l.quantity() <= 0) throw ApiException.badRequest("Số lượng của \"" + it.getName() + "\" phải lớn hơn 0");
            if (l.unitPrice() == null || l.unitPrice().signum() <= 0) throw ApiException.badRequest("Đơn giá của \"" + it.getName() + "\" phải lớn hơn 0");
            result.add(new ParsedLine(it, l.quantity(), l.unitPrice()));
        }
        return result;
    }

    @Transactional
    public ReceiptDto create(ReceiptRequest req, MultipartFile invoiceFile, User actor) {
        Supplier supplier = supplierRepository.findById(req.supplierId())
                .orElseThrow(() -> ApiException.badRequest("Nhà cung cấp không tồn tại"));
        if (!supplier.isActive()) throw ApiException.badRequest("Nhà cung cấp \"" + supplier.getName() + "\" đã ngừng hợp tác");
        if (req.receiptDate().isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày nhập không được sau ngày hôm nay");
        if (req.dueDate() != null && req.dueDate().isBefore(req.receiptDate())) throw ApiException.badRequest("Hạn thanh toán phải từ ngày nhập trở đi");
        String invoiceNo = req.invoiceNo().trim();
        findDuplicateInvoice(supplier.getId(), invoiceNo, null).ifPresent(code -> {
            throw ApiException.conflict("DUPLICATE_INVOICE", "Số hóa đơn \"" + invoiceNo + "\" của nhà cung cấp này đã được nhập ở phiếu " + code
                    + ". Vui lòng kiểm tra lại.");
        });
        List<ParsedLine> lines = parseLines(req.lines(), Map.of());

        GoodsReceipt r = new GoodsReceipt();
        r.setCode(CodeGenerator.next("PN", req.receiptDate(), receiptRepository::countByCodeStartingWith));
        r.setSupplier(supplier);
        r.setInvoiceNo(invoiceNo);
        r.setReceiptDate(req.receiptDate());
        r.setDueDate(req.dueDate());
        r.setNote(TextUtil.trimToNull(req.note()));
        r.setCreatedBy(actor);
        if (invoiceFile != null && !invoiceFile.isEmpty()) {
            r.setInvoiceFile(fileStorageService.store(invoiceFile, FileStorageService.EVIDENCE_EXT, "Ảnh/tệp hóa đơn", "INVOICE", actor));
        }
        receiptRepository.save(r);

        var batch = inventoryService.batch();
        int no = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (ParsedLine pl : lines) {
            GoodsReceiptLine l = new GoodsReceiptLine();
            l.setReceipt(r);
            l.setItem(pl.item());
            l.setLineNo(no++);
            l.setQuantity(pl.quantity());
            l.setUnitPrice(pl.unitPrice());
            l.setLineAmount(pl.unitPrice().multiply(BigDecimal.valueOf(pl.quantity())));
            r.getLines().add(l);
            total = total.add(l.getLineAmount());
            inventoryService.change(batch, pl.item(), pl.quantity(), MovementType.RECEIPT, r.getReceiptDate(),
                    "RECEIPT", r.getId(), r.getCode(), "Nhập hàng từ " + supplier.getName(), actor, false);
            pl.item().setLastCost(pl.unitPrice());
        }
        r.setTotalAmount(total);
        recomputeDebt(r);
        inventoryService.finish(batch, "nhập hàng");
        notificationService.notifyRoles(EnumSet.of(Role.ACCOUNTANT), NotificationType.DEBT,
                "Công nợ mới: " + r.getCode(),
                supplier.getName() + " – hóa đơn " + invoiceNo + ", " + String.format("%,.0f", total) + " đ (chưa thanh toán)",
                "/phieu-nhap/" + r.getId());
        return toDto(r, true, true);
    }

    /** Tính lại số đã trả, còn nợ, trạng thái công nợ của phiếu. */
    public void recomputeDebt(GoodsReceipt r) {
        BigDecimal paid = r.getId() == null ? BigDecimal.ZERO : paymentRepository.findByReceiptIdOrderByCreatedAtDesc(r.getId()).stream()
                .filter(p -> p.getStatus() == DocStatus.ACTIVE).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        r.setPaidAmount(paid);
        r.setRemainingAmount(r.getTotalAmount().subtract(paid).max(BigDecimal.ZERO));
        if (paid.signum() == 0) r.setDebtStatus(DebtStatus.UNPAID);
        else if (paid.compareTo(r.getTotalAmount()) >= 0) r.setDebtStatus(DebtStatus.PAID);
        else r.setDebtStatus(DebtStatus.PARTIAL);
    }

    // ---------- Chỉnh sửa / hủy (UC_12) ----------

    private void ensureEditable(GoodsReceipt r) {
        if (r.getStatus() == DocStatus.CANCELLED) throw ApiException.badRequest("Phiếu nhập đã bị hủy, không thể thao tác");
        if (paymentRepository.existsByReceiptIdAndStatus(r.getId(), DocStatus.ACTIVE)) {
            throw ApiException.conflict("HAS_PAYMENT", "Phiếu nhập đã có khoản thanh toán. Vui lòng yêu cầu Kế toán hủy khoản thanh toán trước khi sửa hoặc hủy phiếu.");
        }
    }

    private void precheckStock(Map<Item, Integer> deltas) {
        List<String> problems = new ArrayList<>();
        deltas.forEach((item, d) -> {
            if (item.getCurrentStock() + d < 0) {
                problems.add(item.getName() + ": tồn hiện tại " + item.getCurrentStock() + ", thay đổi " + d);
            }
        });
        if (!problems.isEmpty()) {
            throw ApiException.conflict("NEGATIVE_STOCK", "Thao tác làm tồn kho bị âm ở " + problems.size() + " mặt hàng", problems);
        }
    }

    @Transactional
    public ReceiptDto update(String id, ReceiptRequest req, User actor) {
        if (TextUtil.isBlank(req.reason())) throw ApiException.badRequest("Vui lòng nhập lý do chỉnh sửa");
        GoodsReceipt r = find(id);
        ensureEditable(r);
        Supplier supplier = supplierRepository.findById(req.supplierId())
                .orElseThrow(() -> ApiException.badRequest("Nhà cung cấp không tồn tại"));
        if (req.receiptDate().isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày nhập không được sau ngày hôm nay");
        String invoiceNo = req.invoiceNo().trim();
        findDuplicateInvoice(supplier.getId(), invoiceNo, r.getId()).ifPresent(code -> {
            throw ApiException.conflict("DUPLICATE_INVOICE", "Số hóa đơn \"" + invoiceNo + "\" đã được nhập ở phiếu " + code);
        });
        Map<String, Integer> oldQty = new HashMap<>();
        r.getLines().forEach(l -> oldQty.merge(l.getItem().getId(), l.getQuantity(), Integer::sum));
        List<ParsedLine> newLines = parseLines(req.lines(), oldQty);

        Map<Item, Integer> deltas = new LinkedHashMap<>();
        Map<String, Item> byId = new HashMap<>();
        r.getLines().forEach(l -> byId.put(l.getItem().getId(), l.getItem()));
        newLines.forEach(l -> byId.put(l.item().getId(), l.item()));
        Map<String, Integer> newQty = new HashMap<>();
        newLines.forEach(l -> newQty.merge(l.item().getId(), l.quantity(), Integer::sum));
        for (var e : byId.entrySet()) {
            int d = newQty.getOrDefault(e.getKey(), 0) - oldQty.getOrDefault(e.getKey(), 0);
            if (d != 0) deltas.put(e.getValue(), d);
        }
        precheckStock(deltas);

        StringBuilder detail = new StringBuilder();
        detail.append("Tổng tiền: ").append(String.format("%,.0f", r.getTotalAmount()));
        var batch = inventoryService.batch();
        for (var e : deltas.entrySet()) {
            inventoryService.change(batch, e.getKey(), e.getValue(), MovementType.RECEIPT_EDIT, req.receiptDate(),
                    "RECEIPT", r.getId(), r.getCode(), "Sửa phiếu nhập: " + req.reason(), actor, false);
            detail.append("; ").append(e.getKey().getName()).append(" ").append(e.getValue() > 0 ? "+" : "").append(e.getValue());
        }
        r.getLines().clear();
        receiptRepository.flush();
        int no = 1;
        BigDecimal total = BigDecimal.ZERO;
        for (ParsedLine pl : newLines) {
            GoodsReceiptLine l = new GoodsReceiptLine();
            l.setReceipt(r);
            l.setItem(pl.item());
            l.setLineNo(no++);
            l.setQuantity(pl.quantity());
            l.setUnitPrice(pl.unitPrice());
            l.setLineAmount(pl.unitPrice().multiply(BigDecimal.valueOf(pl.quantity())));
            r.getLines().add(l);
            total = total.add(l.getLineAmount());
            pl.item().setLastCost(pl.unitPrice());
        }
        r.setSupplier(supplier);
        r.setInvoiceNo(invoiceNo);
        r.setReceiptDate(req.receiptDate());
        r.setDueDate(req.dueDate());
        r.setNote(TextUtil.trimToNull(req.note()));
        r.setTotalAmount(total);
        recomputeDebt(r);
        detail.append(" → ").append(String.format("%,.0f", total));
        auditService.log("RECEIPT", r.getId(), r.getCode(), "UPDATE", req.reason().trim(), detail.toString(), actor);
        inventoryService.finish(batch, "sửa phiếu nhập " + r.getCode());
        return toDto(r, true, true);
    }

    @Transactional
    public ReceiptDto cancel(String id, String reason, User actor) {
        if (TextUtil.isBlank(reason)) throw ApiException.badRequest("Vui lòng nhập lý do hủy phiếu");
        GoodsReceipt r = find(id);
        ensureEditable(r);
        Map<Item, Integer> deltas = new LinkedHashMap<>();
        r.getLines().forEach(l -> deltas.merge(l.getItem(), -l.getQuantity(), Integer::sum));
        precheckStock(deltas);
        var batch = inventoryService.batch();
        deltas.forEach((item, d) -> inventoryService.change(batch, item, d, MovementType.RECEIPT_CANCEL, r.getReceiptDate(),
                "RECEIPT", r.getId(), r.getCode(), "Hủy phiếu nhập: " + reason, actor, false));
        r.setStatus(DocStatus.CANCELLED);
        r.setCancelledBy(actor);
        r.setCancelReason(reason.trim());
        r.setRemainingAmount(BigDecimal.ZERO);
        auditService.log("RECEIPT", r.getId(), r.getCode(), "CANCEL", reason.trim(), null, actor);
        inventoryService.finish(batch, "hủy phiếu nhập " + r.getCode());
        return toDto(r, true, true);
    }
}
