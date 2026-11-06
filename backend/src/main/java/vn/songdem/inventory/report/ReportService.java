package vn.songdem.inventory.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.domain.Enums.MovementType;
import vn.songdem.inventory.report.ReportTable.Column;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.service.StocktakeService;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** UC_26 – Báo cáo thống kê: tồn kho, nhập–xuất–tồn, tiêu thụ, công nợ, kiểm kê. */
@Service
@RequiredArgsConstructor
public class ReportService {
    private final ItemRepository itemRepository;
    private final StockMovementRepository movementRepository;
    private final SalesExportLineRepository salesLineRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final PaymentRepository paymentRepository;
    private final StocktakeRepository stocktakeRepository;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final Map<String, String> TYPES = new LinkedHashMap<>();
    static {
        TYPES.put("ton-kho", "Báo cáo giá trị tồn kho");
        TYPES.put("nhap-xuat-ton", "Báo cáo nhập – xuất – tồn");
        TYPES.put("tieu-thu", "Báo cáo tiêu thụ sản phẩm");
        TYPES.put("cong-no", "Báo cáo công nợ nhà cung cấp");
        TYPES.put("kiem-ke", "Báo cáo kiểm kê và tỉ lệ chính xác kho");
    }

    private static String period(LocalDate from, LocalDate to) {
        return "Từ ngày " + from.format(DF) + " đến ngày " + to.format(DF);
    }

    @Transactional(readOnly = true)
    public ReportTable build(String type, LocalDate from, LocalDate to, Role role) {
        boolean values = role != Role.STAFF;
        LocalDate t = to == null ? LocalDate.now() : to;
        LocalDate f = from == null ? t.withDayOfMonth(1) : from;
        if (t.isBefore(f)) throw ApiException.badRequest("Ngày kết thúc phải sau ngày bắt đầu");
        ReportTable table = switch (type) {
            case "ton-kho" -> inventory(values);
            case "nhap-xuat-ton" -> movement(f, t, values);
            case "tieu-thu" -> consumption(f, t, values);
            case "cong-no" -> {
                if (!values) throw ApiException.forbidden("Báo cáo công nợ chỉ dành cho Quản lý và Kế toán");
                yield debts(f, t);
            }
            case "kiem-ke" -> stocktakes(f, t);
            default -> throw ApiException.notFound("Không có loại báo cáo này");
        };
        return table;
    }

    private static Map<String, Object> row() {
        return new LinkedHashMap<>();
    }

    private static String statusLabel(String s) {
        return switch (s) {
            case "LOW" -> "Tồn thấp";
            case "OVER" -> "Vượt mức";
            default -> "Bình thường";
        };
    }

    // 1. Giá trị tồn kho tại thời điểm hiện tại
    private ReportTable inventory(boolean values) {
        List<Column> cols = new ArrayList<>(List.of(
                new Column("code", "Mã hàng", "text"), new Column("name", "Tên mặt hàng", "text"),
                new Column("category", "Loại hàng", "text"), new Column("unit", "ĐVT", "text"),
                new Column("stock", "Tồn kho", "int"), new Column("minStock", "Mốc cảnh báo", "int"),
                new Column("status", "Trạng thái", "text")));
        if (values) {
            cols.add(new Column("cost", "Giá vốn gần nhất", "money"));
            cols.add(new Column("value", "Giá trị tồn", "money"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<String, BigDecimal> byCatValue = new LinkedHashMap<>();
        Map<String, Integer> byCatQty = new LinkedHashMap<>();
        for (Item i : itemRepository.findAllWithRefs()) {
            if (!i.isActive() && i.getCurrentStock() == 0) continue;
            BigDecimal cost = i.getLastCost() == null ? BigDecimal.ZERO : i.getLastCost();
            BigDecimal value = cost.multiply(BigDecimal.valueOf(i.getCurrentStock()));
            var r = row();
            r.put("code", i.getCode());
            r.put("name", i.getName());
            r.put("category", i.getCategory().getName());
            r.put("unit", i.getUnit());
            r.put("stock", i.getCurrentStock());
            r.put("minStock", i.getMinStock());
            r.put("status", statusLabel(i.stockStatus()));
            if (values) {
                r.put("cost", cost);
                r.put("value", value);
            }
            rows.add(r);
            totalQty += i.getCurrentStock();
            totalValue = totalValue.add(value);
            byCatValue.merge(i.getCategory().getName(), value, BigDecimal::add);
            byCatQty.merge(i.getCategory().getName(), i.getCurrentStock(), Integer::sum);
        }
        Map<String, Object> totals = row();
        totals.put("name", "Tổng cộng (" + rows.size() + " mặt hàng)");
        totals.put("stock", totalQty);
        if (values) totals.put("value", totalValue);
        List<Map<String, Object>> cat = new ArrayList<>();
        byCatQty.forEach((k, v) -> {
            var m = row();
            m.put("category", k);
            m.put("quantity", v);
            if (values) m.put("value", byCatValue.get(k));
            cat.add(m);
        });
        return new ReportTable("ton-kho", TYPES.get("ton-kho"), "Tại thời điểm " + LocalDate.now().format(DF), cols, rows, totals,
                Map.of("byCategory", cat));
    }

    // 2. Nhập – xuất – tồn theo kỳ
    private ReportTable movement(LocalDate from, LocalDate to, boolean values) {
        List<Column> cols = new ArrayList<>(List.of(
                new Column("code", "Mã hàng", "text"), new Column("name", "Tên mặt hàng", "text"), new Column("unit", "ĐVT", "text"),
                new Column("opening", "Tồn đầu kỳ", "int"), new Column("in", "Nhập trong kỳ", "int"),
                new Column("out", "Xuất bán", "int"), new Column("adjust", "Điều chỉnh / hỏng hủy", "int"),
                new Column("closing", "Tồn cuối kỳ", "int")));
        if (values) cols.add(new Column("closingValue", "Giá trị tồn cuối", "money"));
        Map<String, int[]> agg = new HashMap<>(); // [afterTo, in, out, adjust]
        for (StockMovement m : movementRepository.findFromDate(from)) {
            int[] a = agg.computeIfAbsent(m.getItem().getId(), k -> new int[4]);
            int q = m.getQuantityChange();
            if (m.getMovementDate().isAfter(to)) { a[0] += q; continue; }
            MovementType ty = m.getType();
            switch (ty) {
                case RECEIPT, RECEIPT_EDIT, RECEIPT_CANCEL -> a[1] += q;
                case SALE, SALE_EDIT, SALE_CANCEL -> a[2] -= q;
                case ADJUSTMENT, DAMAGE -> a[3] += q;
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int tOpen = 0, tIn = 0, tOut = 0, tAdj = 0, tClose = 0;
        BigDecimal tValue = BigDecimal.ZERO;
        for (Item i : itemRepository.findAllWithRefs()) {
            int[] a = agg.getOrDefault(i.getId(), new int[4]);
            int closing = i.getCurrentStock() - a[0];
            int opening = closing - a[1] + a[2] - a[3];
            if (!i.isActive() && opening == 0 && closing == 0 && a[1] == 0 && a[2] == 0) continue;
            BigDecimal cost = i.getLastCost() == null ? BigDecimal.ZERO : i.getLastCost();
            var r = row();
            r.put("code", i.getCode());
            r.put("name", i.getName());
            r.put("unit", i.getUnit());
            r.put("opening", opening);
            r.put("in", a[1]);
            r.put("out", a[2]);
            r.put("adjust", a[3]);
            r.put("closing", closing);
            if (values) {
                BigDecimal v = cost.multiply(BigDecimal.valueOf(closing));
                r.put("closingValue", v);
                tValue = tValue.add(v);
            }
            rows.add(r);
            tOpen += opening; tIn += a[1]; tOut += a[2]; tAdj += a[3]; tClose += closing;
        }
        Map<String, Object> totals = row();
        totals.put("name", "Tổng cộng");
        totals.put("opening", tOpen);
        totals.put("in", tIn);
        totals.put("out", tOut);
        totals.put("adjust", tAdj);
        totals.put("closing", tClose);
        if (values) totals.put("closingValue", tValue);
        return new ReportTable("nhap-xuat-ton", TYPES.get("nhap-xuat-ton"), period(from, to) + " · Tồn cuối = Tồn đầu + Nhập − Xuất + Điều chỉnh",
                cols, rows, totals, Map.of());
    }

    // 3. Tiêu thụ theo doanh số POS
    private ReportTable consumption(LocalDate from, LocalDate to, boolean values) {
        List<Column> cols = new ArrayList<>(List.of(
                new Column("rank", "Hạng", "int"), new Column("code", "Mã hàng", "text"), new Column("name", "Tên mặt hàng", "text"),
                new Column("category", "Loại hàng", "text"), new Column("unit", "ĐVT", "text"),
                new Column("quantity", "Số lượng bán", "int"), new Column("share", "Tỉ trọng SL", "percent"),
                new Column("days", "Số ngày có bán", "int")));
        if (values) {
            cols.add(new Column("revenue", "Doanh thu", "money"));
            cols.add(new Column("avgPrice", "Giá bán bình quân", "money"));
        }
        record Agg(Item item, int[] qty, BigDecimal[] revenue, Set<LocalDate> days) {}
        Map<String, Agg> map = new LinkedHashMap<>();
        Map<LocalDate, int[]> dailyQty = new TreeMap<>();
        Map<LocalDate, BigDecimal> dailyRev = new TreeMap<>();
        Map<String, Integer> byCat = new LinkedHashMap<>();
        Map<String, BigDecimal> byCatRev = new LinkedHashMap<>();
        for (SalesExportLine l : salesLineRepository.findActiveInPeriod(from, to)) {
            Agg a = map.computeIfAbsent(l.getItem().getId(), k -> new Agg(l.getItem(), new int[1], new BigDecimal[]{BigDecimal.ZERO}, new HashSet<>()));
            BigDecimal rev = l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity()));
            a.qty()[0] += l.getQuantity();
            a.revenue()[0] = a.revenue()[0].add(rev);
            a.days().add(l.getSalesExport().getSalesDate());
            LocalDate d = l.getSalesExport().getSalesDate();
            dailyQty.computeIfAbsent(d, k -> new int[1])[0] += l.getQuantity();
            dailyRev.merge(d, rev, BigDecimal::add);
            byCat.merge(l.getItem().getCategory().getName(), l.getQuantity(), Integer::sum);
            byCatRev.merge(l.getItem().getCategory().getName(), rev, BigDecimal::add);
        }
        int totalQty = map.values().stream().mapToInt(a -> a.qty()[0]).sum();
        BigDecimal totalRev = map.values().stream().map(a -> a.revenue()[0]).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Agg> sorted = new ArrayList<>(map.values());
        sorted.sort((x, y) -> Integer.compare(y.qty()[0], x.qty()[0]));
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Agg a : sorted) {
            var r = row();
            r.put("rank", rank++);
            r.put("code", a.item().getCode());
            r.put("name", a.item().getName());
            r.put("category", a.item().getCategory().getName());
            r.put("unit", a.item().getUnit());
            r.put("quantity", a.qty()[0]);
            r.put("share", totalQty == 0 ? 0 : Math.round(1000.0 * a.qty()[0] / totalQty) / 10.0);
            r.put("days", a.days().size());
            if (values) {
                r.put("revenue", a.revenue()[0]);
                r.put("avgPrice", a.qty()[0] == 0 ? BigDecimal.ZERO : a.revenue()[0].divide(BigDecimal.valueOf(a.qty()[0]), 0, RoundingMode.HALF_UP));
            }
            rows.add(r);
        }
        // Mặt hàng không bán được trong kỳ (giúp Ban lãnh đạo nhận diện hàng khó bán)
        List<Map<String, Object>> unsold = new ArrayList<>();
        for (Item i : itemRepository.findAllWithRefs()) {
            if (i.isActive() && !map.containsKey(i.getId())) {
                var m = row();
                m.put("code", i.getCode());
                m.put("name", i.getName());
                m.put("stock", i.getCurrentStock());
                unsold.add(m);
            }
        }
        Map<String, Object> totals = row();
        totals.put("name", "Tổng cộng (" + rows.size() + " mặt hàng)");
        totals.put("quantity", totalQty);
        totals.put("share", totalQty == 0 ? 0 : 100.0);
        if (values) totals.put("revenue", totalRev);
        List<Map<String, Object>> daily = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            var m = row();
            m.put("date", d.toString());
            m.put("quantity", dailyQty.containsKey(d) ? dailyQty.get(d)[0] : 0);
            if (values) m.put("revenue", dailyRev.getOrDefault(d, BigDecimal.ZERO));
            daily.add(m);
        }
        List<Map<String, Object>> cat = new ArrayList<>();
        byCat.forEach((k, v) -> {
            var m = row();
            m.put("category", k);
            m.put("quantity", v);
            if (values) m.put("revenue", byCatRev.get(k));
            cat.add(m);
        });
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("daily", daily);
        extra.put("byCategory", cat);
        extra.put("unsold", unsold);
        return new ReportTable("tieu-thu", TYPES.get("tieu-thu"), period(from, to) + " · Theo dữ liệu doanh số POS", cols, rows, totals, extra);
    }

    // 4. Công nợ theo nhà cung cấp
    private ReportTable debts(LocalDate from, LocalDate to) {
        List<Column> cols = List.of(
                new Column("code", "Mã NCC", "text"), new Column("name", "Nhà cung cấp", "text"),
                new Column("opening", "Nợ đầu kỳ", "money"), new Column("purchased", "Phát sinh nhập", "money"),
                new Column("paid", "Đã thanh toán", "money"), new Column("closing", "Nợ cuối kỳ", "money"),
                new Column("receipts", "Số phiếu nhập", "int"), new Column("overdue", "Quá hạn hiện tại", "money"));
        record Agg(Supplier s, BigDecimal[] v, int[] n) {}
        Map<String, Agg> map = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (GoodsReceipt r : receiptRepository.findAllActiveWithSupplier()) {
            if (r.getReceiptDate().isAfter(to)) continue;
            Agg a = map.computeIfAbsent(r.getSupplier().getId(), k -> new Agg(r.getSupplier(),
                    new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}, new int[1]));
            if (r.getReceiptDate().isBefore(from)) a.v()[0] = a.v()[0].add(r.getTotalAmount());
            else { a.v()[1] = a.v()[1].add(r.getTotalAmount()); a.n()[0]++; }
            if (r.getDueDate() != null && r.getDueDate().isBefore(today) && r.getRemainingAmount().signum() > 0) {
                a.v()[3] = a.v()[3].add(r.getRemainingAmount());
            }
        }
        for (Payment p : paymentRepository.findActiveInPeriod(LocalDate.of(2000, 1, 1), to)) {
            Agg a = map.get(p.getReceipt().getSupplier().getId());
            if (a == null || p.getReceipt().getReceiptDate().isAfter(to)) continue;
            if (p.getPaidAt().isBefore(from)) a.v()[0] = a.v()[0].subtract(p.getAmount());
            else a.v()[2] = a.v()[2].add(p.getAmount());
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal tOpen = BigDecimal.ZERO, tPur = BigDecimal.ZERO, tPaid = BigDecimal.ZERO, tClose = BigDecimal.ZERO, tOver = BigDecimal.ZERO;
        int tN = 0;
        for (Agg a : map.values()) {
            BigDecimal closing = a.v()[0].add(a.v()[1]).subtract(a.v()[2]);
            var r = row();
            r.put("code", a.s().getCode());
            r.put("name", a.s().getName());
            r.put("opening", a.v()[0]);
            r.put("purchased", a.v()[1]);
            r.put("paid", a.v()[2]);
            r.put("closing", closing);
            r.put("receipts", a.n()[0]);
            r.put("overdue", a.v()[3]);
            rows.add(r);
            tOpen = tOpen.add(a.v()[0]); tPur = tPur.add(a.v()[1]); tPaid = tPaid.add(a.v()[2]);
            tClose = tClose.add(closing); tOver = tOver.add(a.v()[3]); tN += a.n()[0];
        }
        rows.sort((x, y) -> ((BigDecimal) y.get("closing")).compareTo((BigDecimal) x.get("closing")));
        Map<String, Object> totals = row();
        totals.put("name", "Tổng cộng");
        totals.put("opening", tOpen);
        totals.put("purchased", tPur);
        totals.put("paid", tPaid);
        totals.put("closing", tClose);
        totals.put("receipts", tN);
        totals.put("overdue", tOver);
        return new ReportTable("cong-no", TYPES.get("cong-no"), period(from, to) + " · Nợ cuối = Nợ đầu + Phát sinh − Đã thanh toán",
                cols, rows, totals, Map.of());
    }

    // 5. Kiểm kê và tỉ lệ chính xác kho
    private ReportTable stocktakes(LocalDate from, LocalDate to) {
        List<Column> cols = List.of(
                new Column("code", "Mã đợt", "text"), new Column("date", "Ngày kiểm kê", "date"),
                new Column("status", "Trạng thái", "text"), new Column("total", "Số MH kiểm", "int"),
                new Column("matched", "Số MH khớp", "int"), new Column("variance", "Số MH lệch", "int"),
                new Column("accuracy", "Tỉ lệ chính xác", "percent"), new Column("surplus", "Tổng thừa", "int"),
                new Column("shortage", "Tổng thiếu", "int"), new Column("damaged", "Hỏng/hủy", "int"),
                new Column("rounds", "Số lần kiểm", "int"));
        List<Map<String, Object>> rows = new ArrayList<>();
        int tTotal = 0, tMatched = 0, tVar = 0, tSur = 0, tSho = 0, tDam = 0;
        List<Stocktake> list = new ArrayList<>(stocktakeRepository.findInPeriod(from, to));
        list.sort(Comparator.comparing(Stocktake::getStocktakeDate));
        for (Stocktake s : list) {
            int surplus = 0, shortage = 0, damaged = 0, variance = 0;
            for (StocktakeLine l : s.getLines()) {
                if (l.getInitialVarianceQty() > 0) surplus += l.getInitialVarianceQty();
                if (l.getInitialVarianceQty() < 0) shortage -= l.getInitialVarianceQty();
                if (l.getInitialVarianceQty() != 0) variance++;
                damaged += l.getDamagedQty();
            }
            var r = row();
            r.put("code", s.getCode());
            r.put("date", s.getStocktakeDate().toString());
            r.put("status", StocktakeService.statusLabel(s.getStatus()));
            r.put("total", s.getTotalLines());
            r.put("matched", s.getMatchedLines());
            r.put("variance", variance);
            r.put("accuracy", s.getTotalLines() == 0 ? 0 : Math.round(1000.0 * s.getMatchedLines() / s.getTotalLines()) / 10.0);
            r.put("surplus", surplus);
            r.put("shortage", shortage);
            r.put("damaged", damaged);
            r.put("rounds", s.getRound());
            rows.add(r);
            tTotal += s.getTotalLines(); tMatched += s.getMatchedLines(); tVar += variance;
            tSur += surplus; tSho += shortage; tDam += damaged;
        }
        Collections.reverse(rows);
        Map<String, Object> totals = row();
        totals.put("code", "Tổng (" + rows.size() + " đợt)");
        totals.put("total", tTotal);
        totals.put("matched", tMatched);
        totals.put("variance", tVar);
        totals.put("accuracy", tTotal == 0 ? 0 : Math.round(1000.0 * tMatched / tTotal) / 10.0);
        totals.put("surplus", tSur);
        totals.put("shortage", tSho);
        totals.put("damaged", tDam);
        return new ReportTable("kiem-ke", TYPES.get("kiem-ke"), period(from, to) + " · Tỉ lệ chính xác = số mặt hàng khớp ở lần đếm đầu / tổng số mặt hàng kiểm",
                cols, rows, totals, Map.of());
    }
}
