package vn.songdem.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.dto.CatalogDtos.*;
import vn.songdem.inventory.dto.DebtDtos.Allocation;
import vn.songdem.inventory.dto.DebtDtos.PaymentRequest;
import vn.songdem.inventory.dto.StockDtos.*;
import vn.songdem.inventory.dto.StocktakeDtos.DecisionRequest;
import vn.songdem.inventory.dto.StocktakeDtos.StocktakeDto;
import vn.songdem.inventory.repository.UserRepository;
import vn.songdem.inventory.service.*;
import vn.songdem.inventory.util.BytesMultipartFile;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BusinessFlowTest {
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder encoder;
    @Autowired UserService userService;
    @Autowired CatalogService catalogService;
    @Autowired ReceiptService receiptService;
    @Autowired SalesService salesService;
    @Autowired PaymentService paymentService;
    @Autowired StocktakeService stocktakeService;

    User manager, staff, accountant;
    String supplierId, itemA, itemB;
    String suffix;

    private User user(String name, Role role) {
        User u = new User();
        u.setUsername(name + suffix);
        u.setFullName(name);
        u.setRole(role);
        u.setPasswordHash(encoder.encode("matkhau123"));
        return userRepository.save(u);
    }

    @BeforeEach
    void setup() {
        suffix = UUID.randomUUID().toString().substring(0, 6);
        manager = user("ql", Role.MANAGER);
        staff = user("nv", Role.STAFF);
        accountant = user("kt", Role.ACCOUNTANT);
        String cat = catalogService.createCategory(new CategoryRequest("C" + suffix, "Bia", "Mô tả loại hàng"), manager).id();
        supplierId = catalogService.createSupplier(new SupplierRequest("S" + suffix, "NCC Test", null, null, null, null, null,
                "Chuyển khoản", null, null, null, null, null), null, manager).id();
        itemA = catalogService.createItem(new ItemRequest("A" + suffix, "POSA" + suffix, "Bia A", "Chai", cat, supplierId, 5, null,
                BigDecimal.valueOf(90000)), manager).id();
        itemB = catalogService.createItem(new ItemRequest("B" + suffix, "POSB" + suffix, "Bia B", "Chai", cat, supplierId, 5, null,
                BigDecimal.valueOf(80000)), manager).id();
    }

    private ReceiptDto receive(String invoice, int qtyA, int qtyB) {
        return receiptService.create(new ReceiptRequest(supplierId, invoice, LocalDate.now().minusDays(3), null, null,
                List.of(new ReceiptLineRequest(itemA, qtyA, BigDecimal.valueOf(40000)),
                        new ReceiptLineRequest(itemB, qtyB, BigDecimal.valueOf(30000))), null), null, staff);
    }

    private int stock(String id) {
        return catalogService.getItem(id, true).currentStock();
    }

    private BytesMultipartFile csv(String name, String content) {
        return new BytesMultipartFile("file", name, "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void categoryDescriptionIsMappedByModelMapper() {
        var list = catalogService.listCategories("C" + suffix, null);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).description()).isEqualTo("Mô tả loại hàng");
    }

    @Test
    void receiptIncreasesStockAndCreatesDebt_duplicateInvoiceRejected() {
        ReceiptDto r = receive("HD-" + suffix, 10, 6);
        assertThat(stock(itemA)).isEqualTo(10);
        assertThat(stock(itemB)).isEqualTo(6);
        assertThat(r.totalAmount()).isEqualByComparingTo("580000");
        assertThat(r.debtStatus()).isEqualTo("UNPAID");
        assertThatThrownBy(() -> receive("hd-" + suffix, 1, 1))
                .isInstanceOf(ApiException.class).hasMessageContaining("đã được nhập");
    }

    @Test
    void posImportDeductsStockClampsAtZeroAndRejectsSameDate() {
        receive("HD1-" + suffix, 10, 3);
        LocalDate d = LocalDate.now().minusDays(1);
        String file = "Mã hàng POS,Tên hàng,Số lượng,Đơn giá\nPOSA" + suffix + ",Bia A,4,90000\nPOSB" + suffix + ",Bia B,5,80000\n";
        SalesDto s = salesService.importSales(d, csv("pos.csv", file), manager);
        assertThat(stock(itemA)).isEqualTo(6);
        assertThat(stock(itemB)).isEqualTo(0);
        assertThat(s.overQuantity()).isEqualTo(2);
        assertThatThrownBy(() -> salesService.importSales(d, csv("pos.csv", file), manager))
                .isInstanceOf(ApiException.class).hasMessageContaining("đã được nhập");
        // Mã hàng không có trong danh mục
        assertThatThrownBy(() -> salesService.importSales(d.minusDays(1), csv("pos.csv", "Mã hàng POS,Số lượng\nXYZ,1\n"), manager))
                .isInstanceOf(ApiException.class).hasMessageContaining("không thuộc danh mục");
        // Hủy phiếu xuất hoàn lại đúng lượng đã trừ
        salesService.cancel(s.id(), "Nhập nhầm file", manager);
        assertThat(stock(itemA)).isEqualTo(10);
        assertThat(stock(itemB)).isEqualTo(3);
    }

    @Test
    void paymentRulesAndReceiptLocking() {
        ReceiptDto r = receive("HD2-" + suffix, 10, 0 + 1);
        var evidence = new BytesMultipartFile("evidence", "uy-nhiem-chi.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertThatThrownBy(() -> paymentService.create(new PaymentRequest(List.of(new Allocation(r.id(), BigDecimal.valueOf(999999999))),
                LocalDate.now(), "Chuyển khoản", null), evidence, accountant))
                .isInstanceOf(ApiException.class).hasMessageContaining("lớn hơn số còn nợ");
        paymentService.create(new PaymentRequest(List.of(new Allocation(r.id(), BigDecimal.valueOf(100000))),
                LocalDate.now(), "Chuyển khoản", null), evidence, accountant);
        ReceiptDto after = receiptService.get(r.id(), true);
        assertThat(after.debtStatus()).isEqualTo("PARTIAL");
        assertThat(after.remainingAmount()).isEqualByComparingTo("330000");
        // Phiếu đã có thanh toán thì không được hủy
        assertThatThrownBy(() -> receiptService.cancel(r.id(), "Sai", manager))
                .isInstanceOf(ApiException.class).hasMessageContaining("Kế toán hủy khoản thanh toán");
        // Hủy thanh toán → hủy được phiếu, tồn kho giảm lại
        paymentService.cancel(after.payments().get(0).id(), "Nhập sai số tiền", accountant);
        receiptService.cancel(r.id(), "Nhà cung cấp giao nhầm", manager);
        assertThat(stock(itemA)).isEqualTo(0);
    }

    @Test
    void stocktakeOnlyChangesStockAfterManagerDecision() {
        receive("HD3-" + suffix, 20, 10);
        LocalDate today = LocalDate.now();
        String count = "Mã hàng,Số lượng thực tế\nA" + suffix + ",18\nB" + suffix + ",10\n";
        String damage = "Mã hàng,Số lượng hỏng/hủy,Lý do\nB" + suffix + ",1,Vỡ chai\n";
        StocktakeDto st = stocktakeService.create(today, csv("kk.csv", count), csv("hh.csv", damage), staff);
        assertThat(st.status()).isEqualTo("PENDING_REVIEW");
        assertThat(stock(itemA)).isEqualTo(20); // chưa thay đổi
        var detail = stocktakeService.get(st.id());
        String lineA = detail.lines().stream().filter(l -> l.item().id().equals(itemA)).findFirst().orElseThrow().id();
        String lineB = detail.lines().stream().filter(l -> l.item().id().equals(itemB)).findFirst().orElseThrow().id();
        // B: thực tế 10, sổ sách 10, hỏng 1 → lệch +1
        assertThat(detail.lines().stream().filter(l -> l.id().equals(lineB)).findFirst().orElseThrow().varianceQty()).isEqualTo(1);

        assertThatThrownBy(() -> stocktakeService.decide(st.id(), new DecisionRequest(List.of(lineA), List.of(), "x"), manager))
                .isInstanceOf(ApiException.class).hasMessageContaining("tất cả mặt hàng chênh lệch");
        // Đồng ý A, yêu cầu kiểm kê lại B
        StocktakeDto d1 = stocktakeService.decide(st.id(), new DecisionRequest(List.of(lineA), List.of(lineB), "Kiểm tra lại B"), manager);
        assertThat(d1.status()).isEqualTo("RECOUNT_REQUESTED");
        assertThat(stock(itemA)).isEqualTo(18);
        assertThat(stock(itemB)).isEqualTo(10);
        // File kiểm kê lại có mặt hàng ngoài danh sách → từ chối
        assertThatThrownBy(() -> stocktakeService.resubmit(st.id(), csv("kk2.csv", count), null, staff))
                .isInstanceOf(ApiException.class);
        StocktakeDto d2 = stocktakeService.resubmit(st.id(), csv("kk2.csv", "Mã hàng,Số lượng thực tế\nB" + suffix + ",9\n"),
                csv("hh2.csv", damage), staff);
        assertThat(d2.status()).isEqualTo("PENDING_REVIEW"); // vẫn còn hàng hỏng cần xác nhận
        String lineB2 = stocktakeService.get(st.id()).lines().stream().filter(l -> l.item().id().equals(itemB)).findFirst().orElseThrow().id();
        StocktakeDto d3 = stocktakeService.decide(st.id(), new DecisionRequest(List.of(lineB2), List.of(), "Xác nhận hỏng 1 chai"), manager);
        assertThat(d3.status()).isEqualTo("COMPLETED");
        assertThat(stock(itemB)).isEqualTo(9);
        assertThatThrownBy(() -> stocktakeService.decide(st.id(), new DecisionRequest(List.of(lineB2), List.of(), "lần 2"), manager))
                .isInstanceOf(ApiException.class).hasMessageContaining("đã được xử lý");
    }

    @Test
    void lastManagerCannotBeDeactivatedOrSelfDemoted() {
        assertThatThrownBy(() -> userService.setActive(manager.getId(), false, manager))
                .isInstanceOf(ApiException.class).hasMessageContaining("tự vô hiệu hóa");
    }
}
