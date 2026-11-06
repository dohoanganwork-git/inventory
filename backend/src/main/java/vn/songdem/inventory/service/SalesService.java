package vn.songdem.inventory.service;

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
import vn.songdem.inventory.util.TabularFile;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** UC_11, UC_12, UC_13 – Nhập file doanh số POS và phiếu xuất bán. */
@Service
@RequiredArgsConstructor
public class SalesService {
    private final SalesExportRepository salesRepository;
    private final ItemRepository itemRepository;
    private final StocktakeRepository stocktakeRepository;
    private final InventoryService inventoryService;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    /** Một dòng doanh số đã gộp theo mặt hàng. */
    public record SalesRow(int rowNumber, String posCode, String fileName, int quantity, BigDecimal unitPrice) {}

    // ---------- Đọc file POS ----------

    public List<SalesRow> parse(String filename, byte[] data, List<String> errors) {
        TabularFile tf = TabularFile.read(filename, data);
        LinkedHashMap<String, String[]> spec = new LinkedHashMap<>();
        spec.put("code", new String[]{"Mã hàng POS", "Ma hang POS", "Mã POS", "Mã hàng", "Mã SKU", "SKU", "Mã sản phẩm", "Mã"});
        spec.put("qty", new String[]{"Số lượng", "SL", "Số lượng bán", "SL bán", "Quantity", "Qty"});
        Map<String, String> cols = tf.requireColumns(spec);
        String nameCol = tf.findColumn("Tên hàng", "Tên sản phẩm", "Tên", "Sản phẩm", "Name");
        String priceCol = tf.findColumn("Đơn giá", "Giá bán", "Giá", "Đơn giá bán", "Price");
        String amountCol = tf.findColumn("Thành tiền", "Doanh thu", "Tiền hàng", "Amount");

        Map<String, SalesRow> merged = new LinkedHashMap<>();
        for (TabularFile.Row row : tf.rows()) {
            String code = row.get(cols.get("code"));
            String qtyRaw = row.get(cols.get("qty"));
            if (TextUtil.isBlank(code) && TextUtil.isBlank(qtyRaw)) continue;
            if (TextUtil.isBlank(code)) { errors.add("Dòng " + row.rowNumber() + ": thiếu mã hàng"); continue; }
            if (TextUtil.isBlank(qtyRaw)) continue; // để trống = không bán
            Integer qty;
            try { qty = TextUtil.parseInt(qtyRaw); } catch (NumberFormatException e) { qty = null; }
            if (qty == null || qty < 0) { errors.add("Dòng " + row.rowNumber() + ": số lượng \"" + qtyRaw + "\" không hợp lệ"); continue; }
            if (qty == 0) continue;
            BigDecimal price = null;
            try {
                if (priceCol != null) price = TextUtil.parseDecimal(row.get(priceCol));
                if (price == null && amountCol != null) {
                    BigDecimal amount = TextUtil.parseDecimal(row.get(amountCol));
                    if (amount != null) price = amount.divide(BigDecimal.valueOf(qty), 2, java.math.RoundingMode.HALF_UP);
                }
            } catch (NumberFormatException e) {
                errors.add("Dòng " + row.rowNumber() + ": đơn giá không hợp lệ");
                continue;
            }
            String key = code.trim().toLowerCase(Locale.ROOT);
            SalesRow prev = merged.get(key);
            String name = nameCol == null ? null : row.get(nameCol);
            if (prev == null) {
                merged.put(key, new SalesRow(row.rowNumber(), code.trim(), name, qty, price));
            } else {
                merged.put(key, new SalesRow(prev.rowNumber(), prev.posCode(), prev.fileName(), prev.quantity() + qty,
                        prev.unitPrice() != null ? prev.unitPrice() : price));
            }
        }
        if (merged.isEmpty() && errors.isEmpty()) errors.add("File không có dòng doanh số nào");
        return new ArrayList<>(merged.values());
    }

    private void validateDate(LocalDate date) {
        if (date == null) throw ApiException.badRequest("Vui lòng chọn ngày doanh số");
        if (date.isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày doanh số không được sau ngày hôm nay");
        if (salesRepository.existsBySalesDateAndStatus(date, DocStatus.ACTIVE)) {
            throw ApiException.conflict("DATE_IMPORTED", "Doanh số ngày " + fmt(date) + " đã được nhập. Hệ thống từ chối để tránh trừ tồn kho hai lần.");
        }
        stocktakeRepository.findLatestCompletedDate().ifPresent(d -> {
            if (date.isBefore(d)) {
                throw ApiException.conflict("DATE_RECONCILED", "Ngày " + fmt(date) + " thuộc kỳ đã kiểm kê và đối soát hoàn tất (" + fmt(d) + "), không thể nhập thêm doanh số.");
            }
        });
    }

    static String fmt(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    /** Bước 2–3: kiểm tra và hiển thị bản xem trước. */
    @Transactional(readOnly = true)
    public SalesPreview preview(LocalDate date, MultipartFile file) {
        fileStorageService.validate(file, FileStorageService.DATA_EXT, "File doanh số");
        validateDate(date);
        List<String> errors = new ArrayList<>();
        List<SalesRow> rows = parse(file.getOriginalFilename(), FileStorageService.bytes(file), errors);
        return buildPreview(date, file.getOriginalFilename(), rows, errors);
    }

    private SalesPreview buildPreview(LocalDate date, String fileName, List<SalesRow> rows, List<String> errors) {
        List<SalesPreviewRow> out = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        int totalQty = 0, overItems = 0, lowItems = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (SalesRow r : rows) {
            Optional<Item> oi = itemRepository.findByPosCodeIgnoreCase(r.posCode());
            if (oi.isEmpty()) oi = itemRepository.findByCodeIgnoreCase(r.posCode());
            if (oi.isEmpty()) {
                unknown.add(r.posCode() + (r.fileName() == null ? "" : " (" + r.fileName() + ")"));
                out.add(new SalesPreviewRow(r.rowNumber(), r.posCode(), r.fileName(), null, null, null, null, r.quantity(),
                        r.unitPrice(), 0, 0, 0, false));
                continue;
            }
            Item it = oi.get();
            BigDecimal price = r.unitPrice() != null ? r.unitPrice() : it.getSalePrice();
            int after = Math.max(0, it.getCurrentStock() - r.quantity());
            int over = Math.max(0, r.quantity() - it.getCurrentStock());
            boolean low = after <= it.getMinStock();
            if (over > 0) overItems++;
            if (low) lowItems++;
            totalQty += r.quantity();
            total = total.add(price.multiply(BigDecimal.valueOf(r.quantity())));
            out.add(new SalesPreviewRow(r.rowNumber(), r.posCode(), r.fileName(), it.getId(), it.getCode(), it.getName(),
                    it.getUnit(), r.quantity(), price, it.getCurrentStock(), after, over, low));
        }
        if (!unknown.isEmpty()) {
            errors.add(0, "Có " + unknown.size() + " mã hàng không thuộc danh mục: " + String.join(", ", unknown)
                    + ". Vui lòng bổ sung danh mục hoặc sửa file rồi tải lại.");
        }
        return new SalesPreview(date, fileName, out, errors, totalQty, total, overItems, lowItems, errors.isEmpty() && !out.isEmpty());
    }

    /** Bước 4–7: xác nhận nhập, lưu file gốc, tạo phiếu xuất bán, trừ tồn kho. */
    @Transactional
    public SalesDto importSales(LocalDate date, MultipartFile file, User actor) {
        fileStorageService.validate(file, FileStorageService.DATA_EXT, "File doanh số");
        byte[] data = FileStorageService.bytes(file);
        return importSales(date, file.getOriginalFilename(), file.getContentType(), data, actor);
    }

    @Transactional
    public SalesDto importSales(LocalDate date, String fileName, String contentType, byte[] data, User actor) {
        validateDate(date);
        List<String> errors = new ArrayList<>();
        List<SalesRow> rows = parse(fileName, data, errors);
        SalesPreview pv = buildPreview(date, fileName, rows, errors);
        if (!pv.canImport()) {
            throw ApiException.badRequest(pv.errors().isEmpty() ? "File không có dữ liệu hợp lệ" : pv.errors().get(0), pv.errors());
        }
        StoredFile stored = fileStorageService.storeBytes(fileName, contentType, data, "POS", actor);
        SalesExport s = new SalesExport();
        s.setCode(CodeGenerator.next("PX", date, salesRepository::countByCodeStartingWith));
        s.setSalesDate(date);
        s.setOriginalFile(stored);
        s.setImportedBy(actor);
        salesRepository.save(s);

        var batch = inventoryService.batch();
        int no = 1, totalQty = 0, totalOver = 0;
        BigDecimal total = BigDecimal.ZERO;
        List<String> overList = new ArrayList<>();
        for (SalesPreviewRow r : pv.rows()) {
            Item it = itemRepository.findById(r.itemId()).orElseThrow();
            SalesExportLine l = new SalesExportLine();
            l.setSalesExport(s);
            l.setItem(it);
            l.setLineNo(no++);
            l.setQuantity(r.quantity());
            l.setUnitPrice(r.unitPrice());
            l.setStockBefore(it.getCurrentStock());
            int applied = inventoryService.change(batch, it, -r.quantity(), MovementType.SALE, date, "SALES", s.getId(), s.getCode(),
                    "Xuất bán theo doanh số POS", actor, true);
            l.setDeductedQuantity(-applied);
            l.setStockAfter(it.getCurrentStock());
            l.setOverQuantity(r.quantity() + applied);
            s.getLines().add(l);
            totalQty += r.quantity();
            totalOver += l.getOverQuantity();
            total = total.add(r.unitPrice().multiply(BigDecimal.valueOf(r.quantity())));
            if (l.getOverQuantity() > 0) overList.add(it.getName() + ": bán " + r.quantity() + ", tồn " + l.getStockBefore() + " (vượt " + l.getOverQuantity() + ")");
        }
        s.setTotalQuantity(totalQty);
        s.setTotalAmount(total);
        s.setOverQuantity(totalOver);
        inventoryService.finish(batch, "nhập doanh số POS ngày " + fmt(date));
        if (!overList.isEmpty()) {
            notificationService.notifyRoles(EnumSet.of(Role.MANAGER), NotificationType.OVER_SOLD,
                    "Bán vượt tồn kho ngày " + fmt(date) + " (" + overList.size() + " mặt hàng)",
                    String.join("; ", overList) + ". Tồn kho đã được đặt về 0, vui lòng kiểm tra.", "/phieu-xuat/" + s.getId());
        }
        auditService.log("SALES", s.getId(), s.getCode(), "IMPORT", null, "File: " + fileName, actor);
        return toDto(s, true);
    }

    // ---------- Truy vấn (UC_13) ----------

    @Transactional(readOnly = true)
    public List<SalesDto> list(LocalDate from, LocalDate to, String status, String itemId) {
        Specification<SalesExport> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (from != null) ps.add(cb.greaterThanOrEqualTo(root.get("salesDate"), from));
            if (to != null) ps.add(cb.lessThanOrEqualTo(root.get("salesDate"), to));
            if (status != null && !status.isBlank()) ps.add(cb.equal(root.get("status"), DocStatus.valueOf(status)));
            if (itemId != null && !itemId.isBlank()) {
                var sub = query.subquery(String.class);
                var l = sub.from(SalesExportLine.class);
                sub.select(l.get("salesExport").get("id")).where(cb.equal(l.get("item").get("id"), itemId));
                ps.add(root.get("id").in(sub));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return salesRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "salesDate", "importedAt")).stream()
                .map(s -> toDto(s, false)).toList();
    }

    @Transactional(readOnly = true)
    public SalesDto get(String id) {
        return toDto(find(id), true);
    }

    private SalesExport find(String id) {
        return salesRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu xuất"));
    }

    private SalesDto toDto(SalesExport s, boolean detail) {
        List<SalesLineDto> lines = null;
        List<AuditDto> history = null;
        if (detail) {
            lines = s.getLines().stream().map(l -> new SalesLineDto(l.getId(), RefDto.of(l.getItem()), l.getItem().getUnit(),
                    l.getQuantity(), l.getDeductedQuantity(), l.getUnitPrice(),
                    l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())), l.getStockBefore(), l.getStockAfter(),
                    l.getOverQuantity())).toList();
            history = auditService.history(List.of(s.getId()));
        }
        return new SalesDto(s.getId(), s.getCode(), s.getSalesDate(), s.getTotalQuantity(), s.getTotalAmount(), s.getOverQuantity(),
                s.getStatus().name(), s.getLines().size(), s.getCancelReason(), UserRef.of(s.getImportedBy()),
                UserRef.of(s.getCancelledBy()), s.getImportedAt(), FileDto.of(s.getOriginalFile()), lines, history);
    }

    // ---------- Chỉnh sửa / hủy (UC_12) ----------

    @Transactional
    public SalesDto update(String id, SalesUpdateRequest req, User actor) {
        SalesExport s = find(id);
        if (s.getStatus() == DocStatus.CANCELLED) throw ApiException.badRequest("Phiếu xuất đã bị hủy");
        Map<String, Integer> newQty = new HashMap<>();
        req.lines().forEach(l -> newQty.put(l.lineId(), l.quantity()));
        var batch = inventoryService.batch();
        StringBuilder detail = new StringBuilder();
        int totalQty = 0, totalOver = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (SalesExportLine l : s.getLines()) {
            Integer q = newQty.get(l.getId());
            if (q != null && q != l.getQuantity()) {
                Item it = l.getItem();
                // Hoàn lại lượng đã trừ trước đó, sau đó trừ lại theo số lượng mới
                if (l.getDeductedQuantity() > 0) {
                    inventoryService.change(batch, it, l.getDeductedQuantity(), MovementType.SALE_EDIT, s.getSalesDate(),
                            "SALES", s.getId(), s.getCode(), "Sửa phiếu xuất (hoàn lại): " + req.reason(), actor, true);
                }
                int before = it.getCurrentStock();
                int applied = q == 0 ? 0 : inventoryService.change(batch, it, -q, MovementType.SALE_EDIT, s.getSalesDate(),
                        "SALES", s.getId(), s.getCode(), "Sửa phiếu xuất (trừ lại): " + req.reason(), actor, true);
                detail.append(it.getName()).append(": ").append(l.getQuantity()).append(" → ").append(q).append("; ");
                l.setQuantity(q);
                l.setDeductedQuantity(-applied);
                l.setStockBefore(before);
                l.setStockAfter(it.getCurrentStock());
                l.setOverQuantity(q + applied);
            }
            totalQty += l.getQuantity();
            totalOver += l.getOverQuantity();
            total = total.add(l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())));
        }
        if (detail.isEmpty()) throw ApiException.badRequest("Không có thay đổi nào");
        s.setTotalQuantity(totalQty);
        s.setTotalAmount(total);
        s.setOverQuantity(totalOver);
        auditService.log("SALES", s.getId(), s.getCode(), "UPDATE", req.reason().trim(), detail.toString(), actor);
        inventoryService.finish(batch, "sửa phiếu xuất " + s.getCode());
        return toDto(s, true);
    }

    @Transactional
    public SalesDto cancel(String id, String reason, User actor) {
        if (TextUtil.isBlank(reason)) throw ApiException.badRequest("Vui lòng nhập lý do hủy phiếu");
        SalesExport s = find(id);
        if (s.getStatus() == DocStatus.CANCELLED) throw ApiException.badRequest("Phiếu xuất đã bị hủy");
        var batch = inventoryService.batch();
        for (SalesExportLine l : s.getLines()) {
            if (l.getDeductedQuantity() > 0) {
                inventoryService.change(batch, l.getItem(), l.getDeductedQuantity(), MovementType.SALE_CANCEL, s.getSalesDate(),
                        "SALES", s.getId(), s.getCode(), "Hủy phiếu xuất: " + reason, actor, false);
            }
        }
        s.setStatus(DocStatus.CANCELLED);
        s.setCancelledBy(actor);
        s.setCancelReason(reason.trim());
        auditService.log("SALES", s.getId(), s.getCode(), "CANCEL", reason.trim(), null, actor);
        inventoryService.finish(batch, "hủy phiếu xuất " + s.getCode());
        return toDto(s, true);
    }
}
