package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.domain.Enums.*;
import vn.songdem.inventory.dto.CommonDtos.*;
import vn.songdem.inventory.dto.DebtDtos.*;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.util.TabularFile;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** UC_17 – Đối soát công nợ theo kỳ với file hóa đơn của nhà cung cấp. */
@Service
@RequiredArgsConstructor
public class DebtReconciliationService {
    private final DebtReconciliationRepository repository;
    private final SupplierRepository supplierRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    private record InvoiceRow(int rowNumber, String invoiceNo, LocalDate date, BigDecimal amount, BigDecimal paid) {}

    private List<InvoiceRow> parse(String filename, byte[] data) {
        TabularFile tf = TabularFile.read(filename, data);
        LinkedHashMap<String, String[]> spec = new LinkedHashMap<>();
        spec.put("invoice", new String[]{"Số hóa đơn", "So hoa don", "Số HĐ", "Hóa đơn", "Invoice", "Invoice No"});
        spec.put("amount", new String[]{"Số tiền", "Tổng tiền", "Thành tiền", "Giá trị", "Amount", "Tổng cộng"});
        Map<String, String> cols = tf.requireColumns(spec);
        String dateCol = tf.findColumn("Ngày hóa đơn", "Ngày", "Ngày HĐ", "Date");
        String paidCol = tf.findColumn("Đã thanh toán", "Đã trả", "Số tiền đã thanh toán", "Paid");
        List<InvoiceRow> rows = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TabularFile.Row r : tf.rows()) {
            String inv = r.get(cols.get("invoice"));
            String amt = r.get(cols.get("amount"));
            if (TextUtil.isBlank(inv) && TextUtil.isBlank(amt)) continue;
            if (TextUtil.isBlank(inv)) { errors.add("Dòng " + r.rowNumber() + ": thiếu số hóa đơn"); continue; }
            if (!seen.add(inv.trim().toLowerCase(Locale.ROOT))) { errors.add("Dòng " + r.rowNumber() + ": số hóa đơn " + inv + " bị lặp"); continue; }
            BigDecimal amount, paid = null;
            LocalDate date = null;
            try {
                amount = TextUtil.parseDecimal(amt);
                if (amount == null || amount.signum() < 0) throw new NumberFormatException();
                if (paidCol != null) paid = TextUtil.parseDecimal(r.get(paidCol));
            } catch (NumberFormatException e) {
                errors.add("Dòng " + r.rowNumber() + ": số tiền không hợp lệ");
                continue;
            }
            try {
                if (dateCol != null) date = TabularFile.parseDate(r.get(dateCol));
            } catch (IllegalArgumentException e) {
                errors.add("Dòng " + r.rowNumber() + ": ngày hóa đơn không hợp lệ");
                continue;
            }
            rows.add(new InvoiceRow(r.rowNumber(), inv.trim(), date, amount, paid));
        }
        if (!errors.isEmpty()) throw ApiException.badRequest("File hóa đơn có " + errors.size() + " dòng lỗi", errors);
        if (rows.isEmpty()) throw ApiException.badRequest("File hóa đơn không có dữ liệu");
        return rows;
    }

    @Transactional(readOnly = true)
    public List<DebtReconDto> list() {
        return repository.findAllWithSupplier().stream().map(d -> toDto(d, false)).toList();
    }

    @Transactional(readOnly = true)
    public DebtReconDto get(String id) {
        return toDto(find(id), true);
    }

    private DebtReconciliation find(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy kỳ đối soát"));
    }

    private DebtReconDto toDto(DebtReconciliation d, boolean detail) {
        List<DebtReconLineDto> lines = null;
        List<DebtReconUploadDto> uploads = null;
        List<AuditDto> history = null;
        int unresolved = (int) d.getLines().stream().filter(l -> l.getResult() != DebtReconResult.MATCHED && TextUtil.isBlank(l.getNote())).count();
        if (detail) {
            lines = d.getLines().stream().map(l -> new DebtReconLineDto(l.getId(), l.getLineNo(), l.getInvoiceNo(), l.getInvoiceDate(),
                    l.getReceipt() == null ? null : l.getReceipt().getId(), l.getReceipt() == null ? null : l.getReceipt().getCode(),
                    l.getFileAmount(), l.getFilePaid(), l.getSystemAmount(), l.getSystemPaid(), l.getResult().name(), l.getNote())).toList();
            uploads = d.getUploads().stream().map(u -> new DebtReconUploadDto(u.getId(), FileDto.of(u.getInvoiceFile()),
                    FileDto.of(u.getEvidenceFile()), UserRef.of(u.getUploadedBy()), u.getUploadedAt())).toList();
            history = auditService.history(List.of(d.getId()));
        }
        return new DebtReconDto(d.getId(), d.getCode(), RefDto.of(d.getSupplier()), d.getPeriodFrom(), d.getPeriodTo(), d.getStatus().name(),
                d.getFileTotal(), d.getSystemTotal(), d.getSystemPaid(), d.getMatchedCount(), d.getMismatchCount(), unresolved,
                d.getNote(), UserRef.of(d.getCreatedBy()), UserRef.of(d.getCompletedBy()), d.getCompletedAt(), d.getCreatedAt(),
                FileDto.of(d.getInvoiceFile()), FileDto.of(d.getEvidenceFile()), lines, uploads, history);
    }

    /** Bước 1–5: tạo kỳ đối soát, lưu file và đối chiếu. */
    @Transactional
    public DebtReconDto create(String supplierId, LocalDate from, LocalDate to, MultipartFile invoiceFile, MultipartFile evidenceFile, User actor) {
        Supplier s = supplierRepository.findById(supplierId).orElseThrow(() -> ApiException.badRequest("Vui lòng chọn nhà cung cấp"));
        if (from == null || to == null) throw ApiException.badRequest("Vui lòng chọn kỳ đối soát");
        if (to.isBefore(from)) throw ApiException.badRequest("Ngày kết thúc kỳ phải sau ngày bắt đầu");
        for (DebtReconciliation o : repository.findOverlapping(supplierId, from, to)) {
            if (o.getStatus() == DebtReconStatus.COMPLETED) {
                throw ApiException.conflict("PERIOD_COMPLETED", "Kỳ " + SalesService.fmt(o.getPeriodFrom()) + " – " + SalesService.fmt(o.getPeriodTo())
                        + " (" + o.getCode() + ") đã được đối soát hoàn tất, không thể đối soát lại.");
            }
            throw ApiException.conflict("PERIOD_IN_PROGRESS", "Đã có kỳ đối soát " + o.getCode() + " đang thực hiện trùng khoảng thời gian. Vui lòng mở kỳ đó và tải lại file.");
        }
        fileStorageService.validate(invoiceFile, FileStorageService.DATA_EXT, "File hóa đơn");
        if (evidenceFile != null && !evidenceFile.isEmpty()) fileStorageService.validate(evidenceFile, FileStorageService.EVIDENCE_EXT, "Minh chứng thanh toán");
        byte[] data = FileStorageService.bytes(invoiceFile);
        List<InvoiceRow> rows = parse(invoiceFile.getOriginalFilename(), data);

        DebtReconciliation d = new DebtReconciliation();
        d.setCode(CodeGenerator.next("DS", LocalDate.now(), repository::countByCodeStartingWith));
        d.setSupplier(s);
        d.setPeriodFrom(from);
        d.setPeriodTo(to);
        d.setCreatedBy(actor);
        repository.save(d);
        attachFiles(d, invoiceFile, data, evidenceFile, actor);
        match(d, rows);
        auditService.log("DEBT_RECON", d.getId(), d.getCode(), "CREATE", null,
                "Khớp " + d.getMatchedCount() + ", chênh lệch " + d.getMismatchCount(), actor);
        return toDto(d, true);
    }

    private void attachFiles(DebtReconciliation d, MultipartFile invoiceFile, byte[] data, MultipartFile evidenceFile, User actor) {
        StoredFile inv = fileStorageService.storeBytes(invoiceFile.getOriginalFilename(), invoiceFile.getContentType(), data, "DEBT_INVOICE", actor);
        StoredFile ev = null;
        if (evidenceFile != null && !evidenceFile.isEmpty()) {
            ev = fileStorageService.store(evidenceFile, FileStorageService.EVIDENCE_EXT, "Minh chứng thanh toán", "EVIDENCE", actor);
        }
        d.setInvoiceFile(inv);
        if (ev != null) d.setEvidenceFile(ev);
        DebtReconciliationUpload u = new DebtReconciliationUpload();
        u.setReconciliation(d);
        u.setInvoiceFile(inv);
        u.setEvidenceFile(ev);
        u.setUploadedBy(actor);
        d.getUploads().add(u);
    }

    /** Đối chiếu dữ liệu file với phiếu nhập và khoản thanh toán trong kỳ. */
    private void match(DebtReconciliation d, List<InvoiceRow> rows) {
        Map<String, String> oldNotes = new HashMap<>();
        d.getLines().forEach(l -> { if (!TextUtil.isBlank(l.getNote())) oldNotes.put(l.getInvoiceNo().toLowerCase(Locale.ROOT), l.getNote()); });
        d.getLines().clear();
        repository.flush();
        List<GoodsReceipt> inPeriod = receiptRepository.findActiveBySupplierInPeriod(d.getSupplier().getId(), d.getPeriodFrom(), d.getPeriodTo());
        Set<String> matchedReceiptIds = new HashSet<>();
        int no = 1, ok = 0, bad = 0;
        BigDecimal fileTotal = BigDecimal.ZERO, sysTotal = BigDecimal.ZERO, sysPaid = BigDecimal.ZERO;
        for (InvoiceRow row : rows) {
            DebtReconciliationLine l = new DebtReconciliationLine();
            l.setReconciliation(d);
            l.setLineNo(no++);
            l.setInvoiceNo(row.invoiceNo());
            l.setInvoiceDate(row.date());
            l.setFileAmount(row.amount());
            l.setFilePaid(row.paid());
            fileTotal = fileTotal.add(row.amount());
            Optional<GoodsReceipt> or = receiptRepository.findFirstBySupplierIdAndInvoiceNoIgnoreCaseAndStatus(
                    d.getSupplier().getId(), row.invoiceNo(), DocStatus.ACTIVE);
            if (or.isEmpty()) {
                l.setResult(DebtReconResult.NOT_IN_SYSTEM);
                l.setNote(oldNotes.get(row.invoiceNo().toLowerCase(Locale.ROOT)));
            } else {
                GoodsReceipt r = or.get();
                matchedReceiptIds.add(r.getId());
                l.setReceipt(r);
                l.setSystemAmount(r.getTotalAmount());
                l.setSystemPaid(r.getPaidAmount());
                if (r.getTotalAmount().compareTo(row.amount()) != 0) l.setResult(DebtReconResult.AMOUNT_MISMATCH);
                else if (row.paid() != null && r.getPaidAmount().compareTo(row.paid()) != 0) l.setResult(DebtReconResult.PAID_MISMATCH);
                else l.setResult(DebtReconResult.MATCHED);
                if (l.getResult() != DebtReconResult.MATCHED) l.setNote(oldNotes.get(row.invoiceNo().toLowerCase(Locale.ROOT)));
            }
            d.getLines().add(l);
        }
        for (GoodsReceipt r : inPeriod) {
            if (matchedReceiptIds.contains(r.getId())) continue;
            DebtReconciliationLine l = new DebtReconciliationLine();
            l.setReconciliation(d);
            l.setLineNo(no++);
            l.setInvoiceNo(r.getInvoiceNo());
            l.setInvoiceDate(r.getReceiptDate());
            l.setReceipt(r);
            l.setSystemAmount(r.getTotalAmount());
            l.setSystemPaid(r.getPaidAmount());
            l.setResult(DebtReconResult.NOT_IN_FILE);
            l.setNote(oldNotes.get(r.getInvoiceNo().toLowerCase(Locale.ROOT)));
            d.getLines().add(l);
        }
        Set<String> counted = new HashSet<>();
        for (DebtReconciliationLine l : d.getLines()) {
            if (l.getResult() == DebtReconResult.MATCHED) ok++; else bad++;
            GoodsReceipt r = l.getReceipt();
            if (r != null && counted.add(r.getId())) {
                sysTotal = sysTotal.add(r.getTotalAmount());
                sysPaid = sysPaid.add(r.getPaidAmount());
                r.setReconcileStatus(l.getResult() == DebtReconResult.MATCHED ? ReconcileStatus.MATCHED : ReconcileStatus.MISMATCHED);
                r.setReconcileNote(l.getResult() == DebtReconResult.MATCHED ? null : resultLabel(l.getResult()));
            }
        }
        d.setMatchedCount(ok);
        d.setMismatchCount(bad);
        d.setFileTotal(fileTotal);
        d.setSystemTotal(sysTotal);
        d.setSystemPaid(sysPaid);
    }

    static String resultLabel(DebtReconResult r) {
        return switch (r) {
            case MATCHED -> "Khớp";
            case AMOUNT_MISMATCH -> "Lệch tổng tiền";
            case PAID_MISMATCH -> "Lệch số đã thanh toán";
            case NOT_IN_SYSTEM -> "Hóa đơn chưa có phiếu nhập";
            case NOT_IN_FILE -> "Phiếu nhập không có trong file hóa đơn";
        };
    }

    /** 6a – Tải lại file sau khi xử lý chênh lệch. */
    @Transactional
    public DebtReconDto reupload(String id, MultipartFile invoiceFile, MultipartFile evidenceFile, User actor) {
        DebtReconciliation d = find(id);
        if (d.getStatus() == DebtReconStatus.COMPLETED) throw ApiException.conflict("PERIOD_COMPLETED", "Kỳ đối soát đã hoàn tất, không thể tải lại file");
        fileStorageService.validate(invoiceFile, FileStorageService.DATA_EXT, "File hóa đơn");
        byte[] data = FileStorageService.bytes(invoiceFile);
        List<InvoiceRow> rows = parse(invoiceFile.getOriginalFilename(), data);
        attachFiles(d, invoiceFile, data, evidenceFile, actor);
        match(d, rows);
        auditService.log("DEBT_RECON", d.getId(), d.getCode(), "REUPLOAD", null,
                "Khớp " + d.getMatchedCount() + ", chênh lệch " + d.getMismatchCount(), actor);
        return toDto(d, true);
    }

    /** Đối chiếu lại với dữ liệu hiện tại (sau khi chỉnh sửa khoản thanh toán) mà không cần tải file mới. */
    @Transactional
    public DebtReconDto rematch(String id, User actor) {
        DebtReconciliation d = find(id);
        if (d.getStatus() == DebtReconStatus.COMPLETED) throw ApiException.conflict("PERIOD_COMPLETED", "Kỳ đối soát đã hoàn tất");
        var loaded = fileStorageService.load(d.getInvoiceFile().getId());
        match(d, parse(loaded.file().getOriginalName(), loaded.data()));
        auditService.log("DEBT_RECON", d.getId(), d.getCode(), "REMATCH", null,
                "Khớp " + d.getMatchedCount() + ", chênh lệch " + d.getMismatchCount(), actor);
        return toDto(d, true);
    }

    @Transactional
    public DebtReconDto updateLineNote(String id, String lineId, String note, User actor) {
        DebtReconciliation d = find(id);
        if (d.getStatus() == DebtReconStatus.COMPLETED) throw ApiException.conflict("PERIOD_COMPLETED", "Kỳ đối soát đã hoàn tất");
        DebtReconciliationLine l = d.getLines().stream().filter(x -> x.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng đối soát"));
        l.setNote(TextUtil.trimToNull(note));
        return toDto(d, true);
    }

    /** Bước 7 – Xác nhận hoàn tất và khóa kỳ. */
    @Transactional
    public DebtReconDto complete(String id, String note, User actor) {
        DebtReconciliation d = find(id);
        if (d.getStatus() == DebtReconStatus.COMPLETED) throw ApiException.conflict("PERIOD_COMPLETED", "Kỳ đối soát đã hoàn tất");
        List<String> unresolved = d.getLines().stream()
                .filter(l -> l.getResult() != DebtReconResult.MATCHED && TextUtil.isBlank(l.getNote()))
                .map(l -> l.getInvoiceNo() + ": " + resultLabel(l.getResult())).toList();
        if (!unresolved.isEmpty()) {
            throw ApiException.badRequest("Còn " + unresolved.size() + " mục chênh lệch chưa được xử lý. Vui lòng xử lý (và tải lại file) hoặc ghi chú cho từng mục trước khi hoàn tất.", unresolved);
        }
        d.setStatus(DebtReconStatus.COMPLETED);
        d.setNote(TextUtil.trimToNull(note));
        d.setCompletedBy(actor);
        d.setCompletedAt(LocalDateTime.now());
        for (DebtReconciliationLine l : d.getLines()) {
            if (l.getReceipt() != null) l.getReceipt().setReconciledAt(LocalDateTime.now());
        }
        auditService.log("DEBT_RECON", d.getId(), d.getCode(), "COMPLETE", note, null, actor);
        return toDto(d, true);
    }

    /** Mở lại kỳ đối soát để chỉnh sửa khoản thanh toán thuộc kỳ. */
    @Transactional
    public DebtReconDto reopen(String id, String reason, User actor) {
        if (TextUtil.isBlank(reason)) throw ApiException.badRequest("Vui lòng nhập lý do mở lại kỳ đối soát");
        DebtReconciliation d = find(id);
        if (d.getStatus() != DebtReconStatus.COMPLETED) throw ApiException.badRequest("Kỳ đối soát chưa hoàn tất");
        d.setStatus(DebtReconStatus.IN_PROGRESS);
        d.setCompletedAt(null);
        d.setCompletedBy(null);
        auditService.log("DEBT_RECON", d.getId(), d.getCode(), "REOPEN", reason.trim(), null, actor);
        return toDto(d, true);
    }
}
