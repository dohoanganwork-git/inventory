package vn.songdem.inventory.config;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.dto.CatalogDtos.*;
import vn.songdem.inventory.dto.DebtDtos.Allocation;
import vn.songdem.inventory.dto.DebtDtos.PaymentRequest;
import vn.songdem.inventory.dto.StockDtos.ReceiptDto;
import vn.songdem.inventory.dto.StockDtos.ReceiptLineRequest;
import vn.songdem.inventory.dto.StockDtos.ReceiptRequest;
import vn.songdem.inventory.dto.StocktakeDtos.DecisionRequest;
import vn.songdem.inventory.dto.StocktakeDtos.StocktakeDto;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.service.*;
import vn.songdem.inventory.util.BytesMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

/**
 * Tạo dữ liệu mẫu cho cơ sở LongWaits Jazz Club khi CSDL còn trống.
 * Dữ liệu được tạo qua các service nghiệp vụ để tồn kho, thẻ kho và công nợ luôn nhất quán.
 * Thông tin liên hệ nhà cung cấp và tên người dùng là dữ liệu giả lập phục vụ demo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final CatalogService catalogService;
    private final ReceiptService receiptService;
    private final SalesService salesService;
    private final PaymentService paymentService;
    private final StocktakeService stocktakeService;
    private final DebtReconciliationService reconService;

    @Value("${app.seed.enabled:true}")
    private boolean enabled;

    public static final String DEMO_PASSWORD = "songdem123";

    private record ItemSeed(String code, String pos, String name, String unit, String cat, String sup,
                            int min, Integer max, int cost, int price, double rate) {}

    private final Map<String, String> categoryIds = new HashMap<>();
    private final Map<String, String> supplierIds = new HashMap<>();
    private final Map<String, String> itemIds = new LinkedHashMap<>();
    private final Map<String, ItemSeed> seeds = new LinkedHashMap<>();
    private final Random rnd = new Random(2026);

    @Override
    public void run(String... args) {
        if (!enabled || userRepository.count() > 0) return;
        log.info("Đang tạo dữ liệu mẫu...");
        User manager = user("quanly", "Trần Minh Quân", Role.MANAGER, "quanly@example.com", "0900000001", true);
        User staff = user("nhanvien", "Lê Thu Hà", Role.STAFF, "nhanvien@example.com", "0900000002", true);
        User accountant = user("ketoan", "Phạm Ngọc Anh", Role.ACCOUNTANT, "ketoan@example.com", "0900000003", true);
        user("nhanvien2", "Đỗ Văn Bình", Role.STAFF, null, "0900000004", true);
        user("nhanvien.cu", "Nguyễn Thị Cúc", Role.STAFF, null, null, false);

        categories(manager);
        suppliers(manager);
        items(manager);
        timeline(manager, staff, accountant);
        log.info("Đã tạo dữ liệu mẫu. Tài khoản demo: quanly / nhanvien / ketoan – mật khẩu: {}", DEMO_PASSWORD);
    }

    private User user(String username, String name, Role role, String email, String phone, boolean active) {
        User u = new User();
        u.setUsername(username);
        u.setFullName(name);
        u.setRole(role);
        u.setEmail(email);
        u.setPhone(phone);
        u.setActive(active);
        u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        return userRepository.save(u);
    }

    private void categories(User m) {
        String[][] cats = {
                {"BIA", "Bia thủ công", "Bia craft đóng chai, đóng lon"},
                {"RUOU", "Rượu vang", "Rượu vang đỏ, vang trắng các loại"},
                {"DOCHAI", "Đồ uống đóng chai", "Trà lên men, nước có ga đóng chai"},
                {"DOLON", "Đồ uống đóng lon", "Cider, nước giải khát đóng lon"},
                {"MERCH", "Merchandise", "Đĩa CD, USB album, túi tote và quà lưu niệm"}};
        for (String[] c : cats) {
            categoryIds.put(c[0], catalogService.createCategory(new CategoryRequest(c[0], c[1], c[2]), m).id());
        }
    }

    private void suppliers(User m) {
        LocalDate t = LocalDate.now();
        Object[][] sups = {
                {"BEWINE", "Nhà phân phối rượu vang Bewine", "Nguyễn Văn Khoa", "0901000101", "Chuyển khoản", "HĐ-2025/BW-01", t.minusMonths(10), t.plusMonths(14)},
                {"TETE", "Tê Tê Craft Beer", "Hoàng Minh Tú", "0901000102", "Chuyển khoản", "HĐ-2025/TT-02", t.minusMonths(8), t.plusDays(20)},
                {"THOM", "Thơm Brewing Co.", "Vũ Thảo Linh", "0901000103", "Chuyển khoản", "HĐ-2025/TB-03", t.minusMonths(6), t.plusMonths(18)},
                {"HNCIDER", "Hanoi Cider", "Bùi Đức Long", "0901000104", "Tiền mặt", "HĐ-2025/HC-04", t.minusMonths(12), t.minusDays(10)},
                {"BUNDA", "Nhà phân phối Bundaberg Hà Nội", "Đặng Quốc Huy", "0901000105", "Chuyển khoản", "HĐ-2026/BD-05", t.minusMonths(3), t.plusMonths(9)},
                {"JUNBUN", "Junbuncha Kombucha", "Trịnh Mai Phương", "0901000106", "Chuyển khoản", "HĐ-2026/JB-06", t.minusMonths(4), t.plusMonths(8)},
                {"VANGNK", "Công ty Rượu vang nhập khẩu Hà Nội", "Lý Hải Nam", "0901000107", "Chuyển khoản", "HĐ-2025/VN-07", t.minusMonths(9), t.plusMonths(3)},
                {"LWMERCH", "Xưởng in & sản xuất quà tặng Midnight", "Mai Anh Thư", "0901000108", "Tiền mặt", null, null, null}};
        for (Object[] s : sups) {
            String code = (String) s[0];
            var req = new SupplierRequest(code, (String) s[1], (String) s[2], (String) s[3],
                    "lienhe." + code.toLowerCase() + "@example.com", "Hà Nội", null, (String) s[4],
                    s[4].equals("Chuyển khoản") ? "Ngân hàng mẫu – STK 0000" + s[3].toString().substring(6) : null,
                    (String) s[5], (LocalDate) s[6], (LocalDate) s[7],
                    s[5] == null ? null : "Giao hàng tại cơ sở LongWaits, thanh toán trong 30 ngày kể từ ngày nhận hàng.");
            BytesMultipartFile contract = s[5] == null ? null : new BytesMultipartFile("contractFile", "hop-dong-" + code.toLowerCase() + ".pdf",
                    "application/pdf", pdf("HỢP ĐỒNG CUNG CẤP " + s[5], "Bên A: Công ty TNHH Sông Đêm – LongWaits Jazz Club",
                    "Bên B: " + s[1], "Tài liệu mẫu phục vụ demo hệ thống."));
            supplierIds.put(code, catalogService.createSupplier(req, contract, m).id());
        }
    }

    private void items(User m) {
        ItemSeed[] list = {
                new ItemSeed("TT-PALE", "SKU1001", "Bia Tê Tê Craft Beer Pale Ale 330ml", "Chai", "BIA", "TETE", 24, 150, 45000, 95000, 7),
                new ItemSeed("TT-IPA", "SKU1002", "Bia Tê Tê Craft Beer IPA 330ml", "Chai", "BIA", "TETE", 24, 150, 48000, 105000, 6),
                new ItemSeed("THOM-WIT", "SKU1003", "Bia thủ công Thơm Brewing Witbier 330ml", "Chai", "BIA", "THOM", 24, 150, 42000, 95000, 5),
                new ItemSeed("THOM-LAG", "SKU1004", "Bia thủ công Thơm Brewing Lager 330ml", "Chai", "BIA", "THOM", 24, 150, 40000, 90000, 4),
                new ItemSeed("HNC-TAO", "SKU1005", "Hanoi Cider vị Táo lon 330ml", "Lon", "DOLON", "HNCIDER", 24, 120, 32000, 75000, 5),
                new ItemSeed("HNC-DAO", "SKU1006", "Hanoi Cider vị Đào lon 330ml", "Lon", "DOLON", "HNCIDER", 24, 120, 32000, 75000, 4),
                new ItemSeed("BW-DO", "SKU2001", "Rượu vang đỏ Bewine chai 330ml", "Chai", "RUOU", "BEWINE", 12, 80, 65000, 150000, 3),
                new ItemSeed("BW-TRANG", "SKU2002", "Rượu vang trắng Bewine chai 330ml", "Chai", "RUOU", "BEWINE", 12, 80, 65000, 150000, 2),
                new ItemSeed("RIESLING", "SKU2003", "Rượu vang trắng Riesling 750ml", "Chai", "RUOU", "VANGNK", 6, 30, 320000, 850000, 0.9),
                new ItemSeed("VISTANA", "SKU2004", "Rượu vang đỏ Vistana 750ml", "Chai", "RUOU", "VANGNK", 6, 30, 280000, 750000, 0.8),
                new ItemSeed("JBC-GUNG", "SKU3001", "Trà Junbuncha vị Gừng 330ml", "Chai", "DOCHAI", "JUNBUN", 24, 120, 28000, 65000, 4),
                new ItemSeed("JBC-CHANHLEO", "SKU3002", "Trà Junbuncha vị Chanh leo 330ml", "Chai", "DOCHAI", "JUNBUN", 24, 120, 28000, 65000, 3),
                new ItemSeed("BDB-GINGER", "SKU3003", "Nước có ga Bundaberg Ginger Beer 375ml", "Chai", "DOCHAI", "BUNDA", 24, 120, 30000, 70000, 4),
                new ItemSeed("BDB-PINK", "SKU3004", "Nước có ga Bundaberg Pink Grapefruit 375ml", "Chai", "DOCHAI", "BUNDA", 24, 120, 30000, 70000, 3),
                new ItemSeed("CD-MUSE", "SKU4001", "Đĩa CD The Muse Poetry Jazz", "Đĩa", "MERCH", "LWMERCH", 5, 40, 120000, 250000, 0.6),
                new ItemSeed("CD-STEREO", "SKU4002", "Đĩa CD Stereoscopic", "Đĩa", "MERCH", "LWMERCH", 5, 40, 120000, 250000, 0.4),
                new ItemSeed("CD-CELLO", "SKU4003", "Đĩa CD The Six Cello Suites", "Đĩa", "MERCH", "LWMERCH", 5, 40, 130000, 280000, 0.3),
                new ItemSeed("USB-NANGTHO", "SKU4004", "USB Album Nàng Thơ", "Cái", "MERCH", "LWMERCH", 5, 30, 150000, 300000, 0.5),
                new ItemSeed("TOTE-LW", "SKU4005", "Túi Tote kỉ niệm LongWaits", "Cái", "MERCH", "LWMERCH", 10, 60, 60000, 150000, 1.2)};
        for (ItemSeed s : list) {
            var req = new ItemRequest(s.code(), s.pos(), s.name(), s.unit(), categoryIds.get(s.cat()), supplierIds.get(s.sup()),
                    s.min(), s.max(), BigDecimal.valueOf(s.price()));
            itemIds.put(s.code(), catalogService.createItem(req, m).id());
            seeds.put(s.code(), s);
        }
    }

    // ------------------------------------------------------------------

    private ReceiptDto receipt(User staff, String sup, String invoice, LocalDate date, Integer dueDays, Object... codeQty) {
        List<ReceiptLineRequest> lines = new ArrayList<>();
        for (int i = 0; i < codeQty.length; i += 2) {
            ItemSeed s = seeds.get((String) codeQty[i]);
            lines.add(new ReceiptLineRequest(itemIds.get(s.code()), (Integer) codeQty[i + 1], BigDecimal.valueOf(s.cost())));
        }
        return receiptService.create(new ReceiptRequest(supplierIds.get(sup), invoice, date,
                dueDays == null ? null : date.plusDays(dueDays), null, lines, null), null, staff);
    }

    private int stock(String code) {
        return itemRepository.findById(itemIds.get(code)).orElseThrow().getCurrentStock();
    }

    private void sales(User manager, LocalDate d, String overSoldCode) {
        boolean weekend = d.getDayOfWeek().getValue() >= 5;
        StringBuilder csv = new StringBuilder("Mã hàng POS,Tên hàng,Số lượng,Đơn giá\n");
        int lines = 0;
        for (ItemSeed s : seeds.values()) {
            double mean = s.rate() * (weekend ? 1.5 : 0.85);
            int q = (int) Math.round(Math.max(0, mean + rnd.nextGaussian() * Math.max(0.6, mean * 0.35)));
            int st = stock(s.code());
            if (s.code().equals(overSoldCode)) q = st + 2;
            else q = Math.min(q, st);
            if (q <= 0) continue;
            csv.append(s.pos()).append(',').append('"').append(s.name()).append('"').append(',').append(q).append(',').append(s.price()).append('\n');
            lines++;
        }
        if (lines == 0) return;
        salesService.importSales(d, "doanh-so-pos-" + d + ".csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8), manager);
    }

    private BytesMultipartFile evidence(String title, BigDecimal amount) {
        return new BytesMultipartFile("evidence", "uy-nhiem-chi-" + title.toLowerCase().replace(' ', '-') + ".pdf", "application/pdf",
                pdf("ỦY NHIỆM CHI (CHỨNG TỪ MẪU)", "Nội dung: Thanh toán " + title, "Số tiền: " + String.format("%,.0f", amount) + " đồng",
                        "Tài liệu mẫu phục vụ demo hệ thống."));
    }

    private void pay(User accountant, ReceiptDto r, BigDecimal amount, LocalDate date) {
        paymentService.create(new PaymentRequest(List.of(new Allocation(r.id(), amount)), date, "Chuyển khoản", "Thanh toán hóa đơn " + r.invoiceNo()),
                evidence(r.invoiceNo(), amount), accountant);
    }

    /** File kiểm kê: lấy tồn sổ sách, chỉnh lệch theo yêu cầu. */
    private BytesMultipartFile countFile(Map<String, Integer> diff) {
        StringBuilder csv = new StringBuilder("Mã hàng,Tên hàng,Số lượng thực tế\n");
        for (ItemSeed s : seeds.values()) {
            int q = Math.max(0, stock(s.code()) + diff.getOrDefault(s.code(), 0));
            csv.append(s.code()).append(",\"").append(s.name()).append("\",").append(q).append('\n');
        }
        return new BytesMultipartFile("countFile", "kiem-ke-ton-thuc-te.csv", "text/csv", csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private BytesMultipartFile damageFile(String code, int qty, String reason) {
        String csv = "Mã hàng,Tên hàng,Số lượng hỏng/hủy,Lý do\n" + code + ",\"" + seeds.get(code).name() + "\"," + qty + ",\"" + reason + "\"\n";
        return new BytesMultipartFile("damageFile", "hang-hong-huy.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private static int roundUp(int q, int step) {
        return ((q + step - 1) / step) * step;
    }

    private void timeline(User manager, User staff, User accountant) {
        LocalDate t = LocalDate.now();
        LocalDate start = t.minusDays(35);
        // Nhập hàng ban đầu
        Map<String, ReceiptDto> r = new LinkedHashMap<>();
        r.put("bw1", receipt(staff, "BEWINE", "BW-0001231", start, 30, "BW-DO", 60, "BW-TRANG", 48));
        r.put("tt1", receipt(staff, "TETE", "TT-2026-0412", start, 30, "TT-PALE", 120, "TT-IPA", 110));
        r.put("th1", receipt(staff, "THOM", "THB-55821", start, 30, "THOM-WIT", 100, "THOM-LAG", 90));
        r.put("hc1", receipt(staff, "HNCIDER", "HC-778120", start, 15, "HNC-TAO", 100, "HNC-DAO", 60));
        r.put("bd1", receipt(staff, "BUNDA", "BD-20260091", start, 30, "BDB-GINGER", 100, "BDB-PINK", 60));
        r.put("jb1", receipt(staff, "JUNBUN", "JB-00318", start, 30, "JBC-GUNG", 100, "JBC-CHANHLEO", 90));
        r.put("vn1", receipt(staff, "VANGNK", "VNK-102233", start, 20, "RIESLING", 24, "VISTANA", 24));
        r.put("lw1", receipt(staff, "LWMERCH", "MD-0042", start, null, "CD-MUSE", 25, "CD-STEREO", 20, "CD-CELLO", 15, "USB-NANGTHO", 18, "TOTE-LW", 50));

        // Một số mặt hàng không được nhập bổ sung để minh họa cảnh báo tồn thấp / hết hàng
        Set<String> neglected = Set.of("RIESLING", "USB-NANGTHO", "BDB-PINK", "CD-CELLO");
        Set<String> latePayers = Set.of("VANGNK", "HNCIDER");
        List<ReceiptDto> autoReceipts = new ArrayList<>();
        int invoiceSeq = 500;

        for (LocalDate d = start; d.isBefore(t); d = d.plusDays(1)) {
            long ago = java.time.temporal.ChronoUnit.DAYS.between(d, t);
            if (ago < 35 && ago > 1) {
                // Nhân viên nhập bổ sung khi mặt hàng chạm mốc cảnh báo
                Map<String, List<Object>> bySupplier = new LinkedHashMap<>();
                for (ItemSeed s : seeds.values()) {
                    if (neglected.contains(s.code())) continue;
                    int st = stock(s.code());
                    if (st <= s.min() + (int) Math.ceil(s.rate() * 2)) {
                        int step = s.unit().equals("Chai") || s.unit().equals("Lon") ? 6 : 5;
                        int target = s.max() != null ? s.max() : s.min() * 4;
                        int qty = roundUp(Math.max(step, target - st - (int) Math.ceil(s.rate() * 3)), step);
                        bySupplier.computeIfAbsent(s.sup(), k -> new ArrayList<>()).addAll(List.of(s.code(), qty));
                    }
                }
                for (var e : bySupplier.entrySet()) {
                    String inv = e.getKey().substring(0, 2) + "-HD" + (invoiceSeq++);
                    autoReceipts.add(receipt(staff, e.getKey(), inv, d, 21, e.getValue().toArray()));
                }
            }
            for (ReceiptDto ar : autoReceipts) {
                if (ar.receiptDate().equals(d.minusDays(14)) && !latePayers.contains(ar.supplier().code())) {
                    pay(accountant, ar, ar.totalAmount(), d);
                }
            }
            if (ago == 30) {
                pay(accountant, r.get("bw1"), r.get("bw1").totalAmount(), d);
                pay(accountant, r.get("th1"), r.get("th1").totalAmount(), d);
            }
            if (ago == 25) {
                pay(accountant, r.get("tt1"), r.get("tt1").totalAmount(), d);
                pay(accountant, r.get("jb1"), BigDecimal.valueOf(3_000_000), d);
                pay(accountant, r.get("lw1"), r.get("lw1").totalAmount(), d);
            }
            if (ago == 22) {
                r.put("tt2", receipt(staff, "TETE", "TT-2026-0498", d, 30, "TT-PALE", 36, "TT-IPA", 24));
            }
            if (ago == 14) {
                // Kiểm kê tuần: lệch nhỏ và một chai vỡ – Quản lý đồng ý điều chỉnh
                StocktakeDto st = stocktakeService.create(d, countFile(Map.of("TT-IPA", -2, "TOTE-LW", 1, "BW-DO", -1)),
                        damageFile("BW-DO", 1, "Vỡ chai khi sắp xếp kệ"), staff);
                List<String> lines = new ArrayList<>();
                stocktakeService.get(st.id()).lines().stream().filter(l -> l.needsDecision()).forEach(l -> lines.add(l.id()));
                stocktakeService.decide(st.id(), new DecisionRequest(lines, List.of(), "Đã kiểm tra lại camera, chênh lệch do bán không ghi nhận"), manager);
            }
            if (ago == 12) {
                pay(accountant, r.get("bd1"), r.get("bd1").totalAmount(), d);
            }
            if (ago == 7) {
                // Kiểm kê tuần: khớp toàn bộ
                stocktakeService.create(d, countFile(Map.of()), null, staff);
            }
            if (ago == 4) {
                pay(accountant, r.get("tt2"), BigDecimal.valueOf(1_500_000), d);
            }
            if (ago == 2) {
                // Nhập dư túi tote cho sự kiện – minh họa cảnh báo tồn vượt mức
                int over = Math.max(5, 60 - stock("TOTE-LW") + 15);
                r.put("lw2", receipt(staff, "LWMERCH", "MD-0057", d, 30, "TOTE-LW", over));
            }
            sales(manager, d, ago == 3 ? "HNC-DAO" : null);
        }

        // Đối soát công nợ: Bewine đã hoàn tất, Tê Tê đang xử lý chênh lệch
        var bwFrom = start.minusDays(5);
        var bwTo = start.plusDays(10);
        StringBuilder bwCsv = new StringBuilder("Số hóa đơn,Ngày hóa đơn,Số tiền,Đã thanh toán\n");
        for (ReceiptDto x : receiptService.list(new ReceiptService.ReceiptFilter(bwFrom, bwTo, supplierIds.get("BEWINE"), null, "ACTIVE", null, null, null), true)) {
            bwCsv.append(x.invoiceNo()).append(',').append(fmt(x.receiptDate())).append(',').append(x.totalAmount().toPlainString())
                 .append(',').append(x.paidAmount().toPlainString()).append('\n');
        }
        var bw = reconService.create(supplierIds.get("BEWINE"), bwFrom, bwTo,
                new BytesMultipartFile("invoiceFile", "bang-ke-hoa-don-bewine.csv", "text/csv", bwCsv.toString().getBytes(StandardCharsets.UTF_8)), null, accountant);
        reconService.complete(bw.id(), "Đã đối chiếu với bảng kê của nhà cung cấp, khớp toàn bộ", accountant);

        StringBuilder ttCsv = new StringBuilder("Số hóa đơn,Ngày hóa đơn,Số tiền,Đã thanh toán\n");
        String tt2Invoice = r.get("tt2").invoiceNo();
        for (ReceiptDto x : receiptService.list(new ReceiptService.ReceiptFilter(start, t.minusDays(10), supplierIds.get("TETE"), null, "ACTIVE", null, null, null), true)) {
            BigDecimal amount = x.invoiceNo().equals(tt2Invoice) ? x.totalAmount().add(BigDecimal.valueOf(240000)) : x.totalAmount();
            ttCsv.append(x.invoiceNo()).append(',').append(fmt(x.receiptDate())).append(',').append(amount.toPlainString())
                 .append(',').append(x.paidAmount().toPlainString()).append('\n');
        }
        ttCsv.append("TT-2026-0455,").append(fmt(start.plusDays(6))).append(",1440000,0\n");
        reconService.create(supplierIds.get("TETE"), start, t.minusDays(10),
                new BytesMultipartFile("invoiceFile", "bang-ke-hoa-don-te-te.csv", "text/csv", ttCsv.toString().getBytes(StandardCharsets.UTF_8)), null, accountant);

        // Các thông báo cũ coi như đã đọc; tạo đợt kiểm kê hôm nay đang chờ Quản lý xem xét
        var old = notificationRepository.findAll();
        old.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(old);
        stocktakeService.create(t, countFile(Map.of("THOM-WIT", -3, "JBC-CHANHLEO", 2, "CD-STEREO", -1)),
                damageFile("HNC-TAO", 2, "Lon bị móp, rò rỉ"), staff);
    }

    private static String fmt(LocalDate d) {
        return String.format("%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }

    private static byte[] pdf(String... lines) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); InputStream font = new ClassPathResource("fonts/DejaVuSans.ttf").getInputStream()) {
            PdfDocument p = new PdfDocument(new PdfWriter(out));
            Document doc = new Document(p);
            doc.setFont(PdfFontFactory.createFont(font.readAllBytes(), PdfEncodings.IDENTITY_H, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED));
            for (String l : lines) doc.add(new Paragraph(l));
            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            return ("%PDF-1.4\n% " + String.join(" ", lines)).getBytes(StandardCharsets.UTF_8);
        }
    }
}
