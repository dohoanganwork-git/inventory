package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.songdem.inventory.domain.Item;
import vn.songdem.inventory.repository.ItemRepository;
import vn.songdem.inventory.web.ApiException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Sinh file Excel mẫu để người dùng điền và tải lên. */
@Service
@RequiredArgsConstructor
public class TemplateService {
    private final ItemRepository itemRepository;

    public record Template(String fileName, byte[] data) {}

    @Transactional(readOnly = true)
    public Template build(String type) {
        List<Item> items = itemRepository.findAllWithRefs().stream().filter(Item::isActive).toList();
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle head = wb.createCellStyle();
            Font f = wb.createFont();
            f.setBold(true);
            f.setColor(IndexedColors.WHITE.getIndex());
            head.setFont(f);
            head.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle input = wb.createCellStyle();
            input.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            input.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            String fileName;
            String[] guide;
            switch (type) {
                case "pos" -> {
                    fileName = "mau-doanh-so-pos.xlsx";
                    Sheet sh = sheet(wb, "Doanh số", head, "Mã hàng POS", "Tên hàng", "Số lượng", "Đơn giá");
                    int r = 1;
                    for (Item i : items) {
                        Row row = sh.createRow(r++);
                        row.createCell(0).setCellValue(i.getPosCode());
                        row.createCell(1).setCellValue(i.getName());
                        row.createCell(2).setCellStyle(input);
                        row.createCell(3).setCellValue(i.getSalePrice() == null ? 0 : i.getSalePrice().doubleValue());
                    }
                    guide = new String[]{
                            "File doanh số cuối ngày xuất từ phần mềm Sapo POS (Excel hoặc CSV).",
                            "Cột bắt buộc: \"Mã hàng POS\" và \"Số lượng\". Cột \"Đơn giá\" (hoặc \"Thành tiền\") dùng cho báo cáo doanh thu.",
                            "Mỗi file chỉ chứa doanh số của MỘT ngày. Ngày được chọn trên màn hình khi tải lên.",
                            "Dòng để trống số lượng được hiểu là không bán. Mã hàng phải trùng với \"Mã POS\" đã khai báo trong danh mục."};
                }
                case "stocktake" -> {
                    fileName = "mau-kiem-ke-ton-thuc-te.xlsx";
                    Sheet sh = sheet(wb, "Kiểm kê", head, "Mã hàng", "Tên hàng", "Đơn vị", "Số lượng thực tế");
                    int r = 1;
                    for (Item i : items) {
                        Row row = sh.createRow(r++);
                        row.createCell(0).setCellValue(i.getCode());
                        row.createCell(1).setCellValue(i.getName());
                        row.createCell(2).setCellValue(i.getUnit());
                        row.createCell(3).setCellStyle(input);
                    }
                    guide = new String[]{
                            "Điền số lượng đếm thực tế (hàng còn bán được) vào cột \"Số lượng thực tế\" cho mọi mặt hàng.",
                            "Hàng hỏng/hủy KHÔNG tính vào số lượng thực tế mà khai báo trong file hỏng/hủy kèm lý do.",
                            "Khi kiểm kê lại theo yêu cầu của Quản lý, chỉ giữ lại các mặt hàng được yêu cầu.",
                            "Hệ thống chỉ đối chiếu và đánh dấu chênh lệch; tồn kho chỉ thay đổi khi Quản lý xác nhận điều chỉnh."};
                }
                case "damage" -> {
                    fileName = "mau-hang-hong-huy.xlsx";
                    Sheet sh = sheet(wb, "Hỏng hủy", head, "Mã hàng", "Tên hàng", "Số lượng hỏng/hủy", "Lý do");
                    for (int r = 1; r <= 5; r++) {
                        Row row = sh.createRow(r);
                        for (int c = 0; c < 4; c++) row.createCell(c).setCellStyle(input);
                    }
                    guide = new String[]{
                            "Liệt kê các mặt hàng hỏng, vỡ, hết hạn... phát hiện khi kiểm kê.",
                            "Bắt buộc nhập \"Lý do\" cho từng dòng (ví dụ: Vỡ chai khi vận chuyển, Hết hạn sử dụng).",
                            "Mặt hàng trong file hỏng/hủy phải có trong file kiểm kê tồn thực tế."};
                }
                case "debt-invoice" -> {
                    fileName = "mau-doi-soat-hoa-don-ncc.xlsx";
                    Sheet sh = sheet(wb, "Hóa đơn", head, "Số hóa đơn", "Ngày hóa đơn", "Số tiền", "Đã thanh toán");
                    for (int r = 1; r <= 5; r++) {
                        Row row = sh.createRow(r);
                        for (int c = 0; c < 4; c++) row.createCell(c).setCellStyle(input);
                    }
                    guide = new String[]{
                            "Bảng kê hóa đơn nhà cung cấp gửi trong kỳ đối soát.",
                            "Cột bắt buộc: \"Số hóa đơn\", \"Số tiền\". Cột \"Đã thanh toán\" (nếu có) dùng đối chiếu với các khoản thanh toán đã ghi nhận.",
                            "Ngày hóa đơn theo định dạng dd/MM/yyyy."};
                }
                default -> throw ApiException.notFound("Không có mẫu file này");
            }
            Sheet g = wb.createSheet("Hướng dẫn");
            g.setColumnWidth(0, 120 * 256);
            for (int i = 0; i < guide.length; i++) g.createRow(i).createCell(0).setCellValue((i + 1) + ". " + guide[i]);
            wb.write(out);
            return new Template(fileName, out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Sheet sheet(Workbook wb, String name, CellStyle head, String... headers) {
        Sheet sh = wb.createSheet(name);
        Row h = sh.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = h.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(head);
            sh.setColumnWidth(i, (i == 1 ? 36 : 20) * 256);
        }
        sh.createFreezePane(0, 1);
        return sh;
    }
}
