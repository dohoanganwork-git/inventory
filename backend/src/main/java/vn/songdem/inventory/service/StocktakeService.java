package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.domain.Enums.*;
import vn.songdem.inventory.dto.CommonDtos.*;
import vn.songdem.inventory.dto.StocktakeDtos.*;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.util.TabularFile;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * UC_19 – UC_23: Kiểm kê và đối soát tồn kho.
 * File kiểm kê chỉ dùng để đối chiếu; tồn kho chỉ thay đổi khi Quản lý xác nhận điều chỉnh.
 */
@Service
@RequiredArgsConstructor
public class StocktakeService {
    private final StocktakeRepository repository;
    private final ItemRepository itemRepository;
    private final SalesExportRepository salesRepository;
    private final InventoryService inventoryService;
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static final Set<StocktakeStatus> OPEN = EnumSet.of(StocktakeStatus.PENDING_REVIEW, StocktakeStatus.RECOUNT_REQUESTED);

    private record CountRow(Item item, int counted) {}
    private record DamageRow(Item item, int qty, String reason) {}
    private record Parsed(Map<String, CountRow> counts, Map<String, DamageRow> damages) {}

    // ---------- Đọc file ----------

    private Item resolveItem(String code) {
        return itemRepository.findByCodeIgnoreCase(code.trim())
                .or(() -> itemRepository.findByPosCodeIgnoreCase(code.trim()))
                .orElse(null);
    }

    private Parsed parse(MultipartFile countFile, MultipartFile damageFile) {
        List<String> errors = new ArrayList<>();
        Map<String, CountRow> counts = new LinkedHashMap<>();
        TabularFile cf = TabularFile.read(countFile.getOriginalFilename(), FileStorageService.bytes(countFile));
        LinkedHashMap<String, String[]> spec = new LinkedHashMap<>();
        spec.put("code", new String[]{"Mã hàng", "Ma hang", "Mã", "Mã POS", "Mã hàng POS", "SKU"});
        spec.put("qty", new String[]{"Số lượng thực tế", "SL thực tế", "Thực tế", "Tồn thực tế", "Số lượng đếm", "Số lượng"});
        Map<String, String> cols = cf.requireColumns(spec);
        for (TabularFile.Row r : cf.rows()) {
            String code = r.get(cols.get("code"));
            String q = r.get(cols.get("qty"));
            if (TextUtil.isBlank(code) && TextUtil.isBlank(q)) continue;
            if (TextUtil.isBlank(code)) { errors.add("File kiểm kê – dòng " + r.rowNumber() + ": thiếu mã hàng"); continue; }
            Item it = resolveItem(code);
            if (it == null) { errors.add("File kiểm kê – dòng " + r.rowNumber() + ": mã hàng \"" + code + "\" không thuộc danh mục"); continue; }
            Integer qty;
            try { qty = TextUtil.parseInt(q); } catch (NumberFormatException e) { qty = null; }
            if (qty == null || qty < 0) { errors.add("File kiểm kê – dòng " + r.rowNumber() + ": số lượng thực tế của " + code + " không hợp lệ"); continue; }
            if (counts.containsKey(it.getId())) { errors.add("File kiểm kê – dòng " + r.rowNumber() + ": mặt hàng " + code + " bị lặp"); continue; }
            counts.put(it.getId(), new CountRow(it, qty));
        }
        Map<String, DamageRow> damages = new LinkedHashMap<>();
        if (damageFile != null && !damageFile.isEmpty()) {
            TabularFile df = TabularFile.read(damageFile.getOriginalFilename(), FileStorageService.bytes(damageFile));
            LinkedHashMap<String, String[]> dspec = new LinkedHashMap<>();
            dspec.put("code", new String[]{"Mã hàng", "Ma hang", "Mã", "Mã POS", "SKU"});
            dspec.put("qty", new String[]{"Số lượng hỏng/hủy", "Số lượng hỏng", "SL hỏng", "SL hủy", "Hỏng/hủy", "Số lượng"});
            dspec.put("reason", new String[]{"Lý do", "Ly do", "Nguyên nhân", "Ghi chú"});
            Map<String, String> dcols = df.requireColumns(dspec);
            for (TabularFile.Row r : df.rows()) {
                String code = r.get(dcols.get("code"));
                String q = r.get(dcols.get("qty"));
                String reason = r.get(dcols.get("reason"));
                if (TextUtil.isBlank(code) && TextUtil.isBlank(q)) continue;
                Item it = TextUtil.isBlank(code) ? null : resolveItem(code);
                if (it == null) { errors.add("File hỏng/hủy – dòng " + r.rowNumber() + ": mã hàng \"" + code + "\" không thuộc danh mục"); continue; }
                Integer qty;
                try { qty = TextUtil.parseInt(q); } catch (NumberFormatException e) { qty = null; }
                if (qty == null || qty <= 0) { errors.add("File hỏng/hủy – dòng " + r.rowNumber() + ": số lượng không hợp lệ"); continue; }
                if (TextUtil.isBlank(reason)) { errors.add("File hỏng/hủy – dòng " + r.rowNumber() + ": thiếu lý do hỏng/hủy"); continue; }
                damages.merge(it.getId(), new DamageRow(it, qty, reason.trim()),
                        (a, b) -> new DamageRow(a.item(), a.qty() + b.qty(), a.reason() + "; " + b.reason()));
            }
        }
        for (DamageRow d : damages.values()) {
            if (!counts.containsKey(d.item().getId())) {
                errors.add("Mặt hàng " + d.item().getCode() + " có trong file hỏng/hủy nhưng không có trong file kiểm kê");
            }
        }
        if (counts.isEmpty() && errors.isEmpty()) errors.add("File kiểm kê không có dòng dữ liệu nào");
        if (!errors.isEmpty()) throw ApiException.badRequest("File kiểm kê có " + errors.size() + " lỗi, vui lòng sửa file và tải lại", errors);
        return new Parsed(counts, damages);
    }

    // ---------- Tạo đợt kiểm kê (UC_19) ----------

    @Transactional
    public StocktakeDto create(LocalDate date, MultipartFile countFile, MultipartFile damageFile, User actor) {
        if (date == null) throw ApiException.badRequest("Vui lòng chọn ngày kiểm kê");
        if (date.isAfter(LocalDate.now())) throw ApiException.badRequest("Ngày kiểm kê không được sau ngày hôm nay");
        fileStorageService.validate(countFile, FileStorageService.DATA_EXT, "File tồn thực tế");
        if (damageFile != null && !damageFile.isEmpty()) fileStorageService.validate(damageFile, FileStorageService.DATA_EXT, "File hỏng/hủy");
        if (repository.existsByStocktakeDateAndStatus(date, StocktakeStatus.COMPLETED)) {
            throw ApiException.conflict("PERIOD_COMPLETED", "Kỳ kiểm kê ngày " + SalesService.fmt(date) + " đã được đối soát hoàn tất");
        }
        List<Stocktake> open = repository.findByStatusIn(OPEN);
        if (!open.isEmpty()) {
            Stocktake o = open.get(0);
            throw ApiException.conflict("OPEN_STOCKTAKE", "Đợt kiểm kê " + o.getCode() + " đang chờ xử lý. Vui lòng mở đợt đó để gửi lại file thay vì tạo đợt mới.");
        }
        Optional<LocalDate> latestSales = salesRepository.findLatestSalesDate();
        if (latestSales.isPresent() && latestSales.get().isBefore(date.minusDays(1))) {
            throw ApiException.conflict("MISSING_POS", "Doanh số POS mới được nhập đến ngày " + SalesService.fmt(latestSales.get())
                    + ". Cần Quản lý nhập doanh số POS đến ngày " + SalesService.fmt(date.minusDays(1)) + " trước khi kiểm kê.");
        }
        Parsed parsed = parse(countFile, damageFile);

        Stocktake s = new Stocktake();
        s.setCode(CodeGenerator.next("KK", date, repository::countByCodeStartingWith));
        s.setStocktakeDate(date);
        s.setCreatedBy(actor);
        repository.save(s);
        StocktakeSubmission sub = storeSubmission(s, "INITIAL", countFile, damageFile, actor);

        int no = 1;
        for (CountRow c : parsed.counts().values()) {
            StocktakeLine l = new StocktakeLine();
            l.setStocktake(s);
            l.setItem(c.item());
            l.setLineNo(no++);
            fill(l, c, parsed.damages().get(c.item().getId()));
            l.setInitialVarianceQty(l.getVarianceQty());
            s.getLines().add(l);
        }
        s.setTotalLines(s.getLines().size());
        s.setMatchedLines((int) s.getLines().stream().filter(l -> l.getInitialVarianceQty() == 0).count());
        evaluate(s, sub, actor);
        auditService.log("STOCKTAKE", s.getId(), s.getCode(), "SUBMIT", null,
                "Gửi file kiểm kê: " + s.getTotalLines() + " mặt hàng", actor);
        return toDto(s, true);
    }

    private void fill(StocktakeLine l, CountRow c, DamageRow d) {
        int book = c.item().getCurrentStock();
        int damaged = d == null ? 0 : d.qty();
        l.setBookQty(book);
        l.setCountedQty(c.counted());
        l.setDamagedQty(damaged);
        l.setDamageReason(d == null ? null : d.reason());
        l.setVarianceQty(c.counted() - (book - damaged));
    }

    private StocktakeSubmission storeSubmission(Stocktake s, String kind, MultipartFile countFile, MultipartFile damageFile, User actor) {
        StoredFile cf = fileStorageService.store(countFile, FileStorageService.DATA_EXT, "File tồn thực tế", "STOCKTAKE", actor);
        StoredFile df = null;
        if (damageFile != null && !damageFile.isEmpty()) {
            df = fileStorageService.store(damageFile, FileStorageService.DATA_EXT, "File hỏng/hủy", "DAMAGE", actor);
        }
        s.setCountFile(cf);
        s.setDamageFile(df);
        StocktakeSubmission sub = new StocktakeSubmission();
        sub.setStocktake(s);
        sub.setKind(kind);
        sub.setCountFile(cf);
        sub.setDamageFile(df);
        sub.setSubmittedBy(actor);
        s.getSubmissions().add(sub);
        return sub;
    }

    /** Xác định trạng thái đợt sau mỗi lần đối chiếu và gửi thông báo. */
    private void evaluate(Stocktake s, StocktakeSubmission sub, User actor) {
        long pending = s.getLines().stream().filter(l -> l.getDecision() == LineDecision.NONE && l.needsDecision()).count();
        sub.setVarianceLines((int) pending);
        if (pending == 0) {
            s.setStatus(StocktakeStatus.COMPLETED);
            s.setDecidedAt(LocalDateTime.now());
            if (s.getDecisionReason() == null) s.setDecisionReason("Kết quả kiểm kê khớp toàn bộ với tồn kho sổ sách");
            notificationService.notifyRoles(EnumSet.of(Role.MANAGER), NotificationType.STOCKTAKE,
                    "Đợt kiểm kê " + s.getCode() + " khớp toàn bộ",
                    "Không có mặt hàng chênh lệch. Đợt kiểm kê đã được hoàn tất tự động.", "/kiem-ke/" + s.getId());
        } else {
            s.setStatus(StocktakeStatus.PENDING_REVIEW);
            notificationService.notifyRoles(EnumSet.of(Role.MANAGER), NotificationType.STOCKTAKE,
                    "Đợt kiểm kê " + s.getCode() + " chờ xem xét",
                    actor.getFullName() + " đã gửi file kiểm kê. Có " + pending + " mặt hàng chênh lệch hoặc hỏng/hủy cần Quản lý quyết định.",
                    "/kiem-ke/" + s.getId());
        }
    }

    // ---------- Gửi lại file (UC_20) ----------

    @Transactional
    public StocktakeDto resubmit(String id, MultipartFile countFile, MultipartFile damageFile, User actor) {
        Stocktake s = find(id);
        if (!OPEN.contains(s.getStatus())) throw ApiException.conflict("LOCKED", "Đợt kiểm kê đã có quyết định hoặc đã khóa, không thể gửi lại file");
        fileStorageService.validate(countFile, FileStorageService.DATA_EXT, "File tồn thực tế");
        if (damageFile != null && !damageFile.isEmpty()) fileStorageService.validate(damageFile, FileStorageService.DATA_EXT, "File hỏng/hủy");
        Parsed parsed = parse(countFile, damageFile);
        boolean recount = s.getStatus() == StocktakeStatus.RECOUNT_REQUESTED;
        boolean hasApproved = s.getLines().stream().anyMatch(l -> l.getDecision() == LineDecision.APPROVED);

        if (!recount && !hasApproved) {
            // Thay toàn bộ file trước khi Quản lý xử lý
            s.getLines().clear();
            repository.flush();
            int no = 1;
            for (CountRow c : parsed.counts().values()) {
                StocktakeLine l = new StocktakeLine();
                l.setStocktake(s);
                l.setItem(c.item());
                l.setLineNo(no++);
                fill(l, c, parsed.damages().get(c.item().getId()));
                l.setInitialVarianceQty(l.getVarianceQty());
                s.getLines().add(l);
            }
            s.setTotalLines(s.getLines().size());
            s.setMatchedLines((int) s.getLines().stream().filter(l -> l.getInitialVarianceQty() == 0).count());
        } else {
            Map<String, StocktakeLine> editable = new LinkedHashMap<>();
            for (StocktakeLine l : s.getLines()) {
                if (recount ? l.getDecision() == LineDecision.RECOUNT : l.getDecision() != LineDecision.APPROVED) {
                    editable.put(l.getItem().getId(), l);
                }
            }
            List<String> errs = new ArrayList<>();
            for (CountRow c : parsed.counts().values()) {
                if (!editable.containsKey(c.item().getId())) {
                    errs.add("Mặt hàng " + c.item().getCode() + " – " + c.item().getName() + " không thuộc danh sách cần kiểm kê lại");
                }
            }
            if (recount) {
                for (StocktakeLine l : editable.values()) {
                    if (!parsed.counts().containsKey(l.getItem().getId())) {
                        errs.add("Thiếu mặt hàng cần kiểm kê lại: " + l.getItem().getCode() + " – " + l.getItem().getName());
                    }
                }
            }
            if (!errs.isEmpty()) throw ApiException.badRequest("File kiểm kê lại không hợp lệ", errs);
            for (CountRow c : parsed.counts().values()) {
                StocktakeLine l = editable.get(c.item().getId());
                fill(l, c, parsed.damages().get(c.item().getId()));
                if (recount) l.setRecountTimes(l.getRecountTimes() + 1);
                l.setDecision(LineDecision.NONE);
            }
            if (recount) s.setRound(s.getRound() + 1);
        }
        StocktakeSubmission sub = storeSubmission(s, recount ? "RECOUNT" : "REPLACE", countFile, damageFile, actor);
        evaluate(s, sub, actor);
        auditService.log("STOCKTAKE", s.getId(), s.getCode(), recount ? "RECOUNT_SUBMIT" : "REPLACE_FILE", null,
                "Gửi lại file: " + parsed.counts().size() + " mặt hàng", actor);
        return toDto(s, true);
    }

    // ---------- Quyết định của Quản lý (UC_22, UC_23) ----------

    @Transactional
    public StocktakeDto decide(String id, DecisionRequest req, User actor) {
        Stocktake s = find(id);
        if (s.getStatus() != StocktakeStatus.PENDING_REVIEW) {
            throw ApiException.conflict("ALREADY_PROCESSED", "Đợt kiểm kê đã được xử lý (trạng thái hiện tại: " + statusLabel(s.getStatus()) + ")");
        }
        if (TextUtil.isBlank(req.reason())) throw ApiException.badRequest("Vui lòng nhập lý do");
        Set<String> approve = new HashSet<>(req.approveLineIds() == null ? List.of() : req.approveLineIds());
        Set<String> recount = new HashSet<>(req.recountLineIds() == null ? List.of() : req.recountLineIds());
        if (approve.isEmpty() && recount.isEmpty()) throw ApiException.badRequest("Vui lòng chọn ít nhất một mặt hàng");
        if (!Collections.disjoint(approve, recount)) throw ApiException.badRequest("Một mặt hàng không thể vừa đồng ý vừa kiểm kê lại");
        Map<String, StocktakeLine> pending = new LinkedHashMap<>();
        s.getLines().stream().filter(l -> l.getDecision() == LineDecision.NONE && l.needsDecision()).forEach(l -> pending.put(l.getId(), l));
        for (String lid : approve) if (!pending.containsKey(lid)) throw ApiException.badRequest("Dòng kiểm kê không hợp lệ hoặc đã được xử lý");
        for (String lid : recount) if (!pending.containsKey(lid)) throw ApiException.badRequest("Dòng kiểm kê không hợp lệ hoặc đã được xử lý");
        List<String> undecided = pending.values().stream().filter(l -> !approve.contains(l.getId()) && !recount.contains(l.getId()))
                .map(l -> l.getItem().getName()).toList();
        if (!undecided.isEmpty()) {
            throw ApiException.badRequest("Vui lòng chọn Đồng ý điều chỉnh hoặc Kiểm kê lại cho tất cả mặt hàng chênh lệch", undecided);
        }

        var batch = inventoryService.batch();
        StringBuilder detail = new StringBuilder();
        for (String lid : approve) {
            StocktakeLine l = pending.get(lid);
            Item it = l.getItem();
            if (l.getDamagedQty() > 0) {
                inventoryService.change(batch, it, -l.getDamagedQty(), MovementType.DAMAGE, s.getStocktakeDate(), "STOCKTAKE",
                        s.getId(), s.getCode(), "Hỏng/hủy: " + l.getDamageReason(), actor, true);
            }
            if (l.getVarianceQty() != 0) {
                inventoryService.change(batch, it, l.getVarianceQty(), MovementType.ADJUSTMENT, s.getStocktakeDate(), "STOCKTAKE",
                        s.getId(), s.getCode(), "Điều chỉnh theo kiểm kê: " + req.reason().trim(), actor, true);
            }
            l.setDecision(LineDecision.APPROVED);
            l.setAdjustedQty(it.getCurrentStock());
            detail.append(it.getName()).append(": ").append(l.getVarianceQty() > 0 ? "+" : "").append(l.getVarianceQty());
            if (l.getDamagedQty() > 0) detail.append(", hỏng/hủy ").append(l.getDamagedQty());
            detail.append("; ");
        }
        List<String> recountNames = new ArrayList<>();
        for (String lid : recount) {
            StocktakeLine l = pending.get(lid);
            l.setDecision(LineDecision.RECOUNT);
            recountNames.add(l.getItem().getCode() + " – " + l.getItem().getName());
        }
        s.setReviewedBy(actor);
        s.setDecidedAt(LocalDateTime.now());
        String reasonText = (s.getRound() > 1 ? "Lần " + s.getRound() + ": " : "") + req.reason().trim();
        s.setDecisionReason(s.getDecisionReason() == null || s.getDecisionReason().startsWith("Kết quả kiểm kê khớp")
                ? reasonText : s.getDecisionReason() + "\n" + reasonText);
        if (!recount.isEmpty()) {
            s.setStatus(StocktakeStatus.RECOUNT_REQUESTED);
            s.setRecountReason(req.reason().trim());
            notificationService.notifyRoles(EnumSet.of(Role.STAFF), NotificationType.RECOUNT,
                    "Yêu cầu kiểm kê lại " + recountNames.size() + " mặt hàng (" + s.getCode() + ")",
                    "Lý do: " + req.reason().trim() + ". Mặt hàng: " + String.join(", ", recountNames), "/kiem-ke/" + s.getId());
            auditService.log("STOCKTAKE", s.getId(), s.getCode(), "REQUEST_RECOUNT", req.reason().trim(), String.join(", ", recountNames), actor);
        } else {
            s.setStatus(StocktakeStatus.COMPLETED);
        }
        if (!approve.isEmpty()) {
            auditService.log("STOCKTAKE", s.getId(), s.getCode(), "APPROVE_ADJUST", req.reason().trim(), detail.toString(), actor);
        }
        inventoryService.finish(batch, "điều chỉnh tồn kho theo kiểm kê " + s.getCode());
        return toDto(s, true);
    }

    // ---------- Truy vấn (UC_21) ----------

    @Transactional(readOnly = true)
    public List<StocktakeDto> list() {
        return repository.findAllByOrderByStocktakeDateDescCreatedAtDesc().stream().map(s -> toDto(s, false)).toList();
    }

    @Transactional(readOnly = true)
    public StocktakeDto get(String id) {
        return toDto(find(id), true);
    }

    private Stocktake find(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt kiểm kê"));
    }

    public static String statusLabel(StocktakeStatus st) {
        return switch (st) {
            case PENDING_REVIEW -> "Chờ xem xét";
            case RECOUNT_REQUESTED -> "Chờ kiểm kê lại";
            case COMPLETED -> "Hoàn tất";
        };
    }

    public StocktakeDto toDto(Stocktake s, boolean detail) {
        int variance = (int) s.getLines().stream().filter(l -> l.getInitialVarianceQty() != 0).count();
        int pending = (int) s.getLines().stream().filter(l -> l.getDecision() == LineDecision.NONE && l.needsDecision()).count();
        int recountLines = (int) s.getLines().stream().filter(l -> l.getDecision() == LineDecision.RECOUNT).count();
        int damaged = s.getLines().stream().mapToInt(StocktakeLine::getDamagedQty).sum();
        double accuracy = s.getTotalLines() == 0 ? 0 : Math.round(1000.0 * s.getMatchedLines() / s.getTotalLines()) / 10.0;
        List<StocktakeLineDto> lines = null;
        List<SubmissionDto> subs = null;
        List<AuditDto> history = null;
        if (detail) {
            lines = s.getLines().stream().map(l -> new StocktakeLineDto(l.getId(), RefDto.of(l.getItem()), l.getItem().getUnit(),
                    l.getBookQty(), l.getDamagedQty(), l.getDamageReason(), l.getBookQty() - l.getDamagedQty(), l.getCountedQty(),
                    l.getVarianceQty(), l.getInitialVarianceQty(), l.getRecountTimes(), l.getDecision().name(), l.getAdjustedQty(),
                    l.needsDecision())).toList();
            subs = s.getSubmissions().stream().map(x -> new SubmissionDto(x.getId(), x.getKind(), FileDto.of(x.getCountFile()),
                    FileDto.of(x.getDamageFile()), UserRef.of(x.getSubmittedBy()), x.getVarianceLines(), x.getSubmittedAt())).toList();
            history = auditService.history(List.of(s.getId()));
        }
        return new StocktakeDto(s.getId(), s.getCode(), s.getStocktakeDate(), s.getStatus().name(), s.getRound(), s.getTotalLines(),
                s.getMatchedLines(), variance, pending, recountLines, accuracy, damaged, s.getDecisionReason(), s.getRecountReason(),
                UserRef.of(s.getCreatedBy()), UserRef.of(s.getReviewedBy()), s.getCreatedAt(), s.getDecidedAt(),
                FileDto.of(s.getCountFile()), FileDto.of(s.getDamageFile()), lines, subs, history);
    }
}
