package vn.songdem.inventory.report;

import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.songdem.inventory.report.ReportTable.Column;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/** Xuất báo cáo ra Excel (Apache POI) và PDF (iText). */
@Component
public class ReportExporter {
    private static final String ORG = "CÔNG TY TNHH SÔNG ĐÊM – LONGWAITS JAZZ CLUB";

    // ---------------- Excel ----------------
    public byte[] toExcel(ReportTable t) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sh = wb.createSheet("Báo cáo");
            DataFormat df = wb.createDataFormat();
            Font bold = wb.createFont();
            bold.setBold(true);
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle title = wb.createCellStyle();
            title.setFont(titleFont);
            CellStyle header = wb.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setWrapText(true);
            border(header);
            CellStyle text = wb.createCellStyle(); border(text);
            CellStyle num = wb.createCellStyle(); border(num); num.setDataFormat(df.getFormat("#,##0"));
            CellStyle pct = wb.createCellStyle(); border(pct); pct.setDataFormat(df.getFormat("0.0\"%\""));
            CellStyle totalText = wb.createCellStyle(); border(totalText); totalText.setFont(bold);
            totalText.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); totalText.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle totalNum = wb.createCellStyle(); totalNum.cloneStyleFrom(totalText); totalNum.setDataFormat(df.getFormat("#,##0"));
            CellStyle totalPct = wb.createCellStyle(); totalPct.cloneStyleFrom(totalText); totalPct.setDataFormat(df.getFormat("0.0\"%\""));

            int n = t.columns().size();
            int r = 0;
            Row r0 = sh.createRow(r++);
            r0.createCell(0).setCellValue(ORG);
            Row r1 = sh.createRow(r++);
            Cell tc = r1.createCell(0);
            tc.setCellValue(t.title().toUpperCase(new Locale("vi")));
            tc.setCellStyle(title);
            sh.addMergedRegion(new CellRangeAddress(1, 1, 0, Math.max(0, n - 1)));
            sh.createRow(r++).createCell(0).setCellValue(t.subtitle());
            sh.createRow(r++).createCell(0).setCellValue("Ngày xuất: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            r++;
            Row hr = sh.createRow(r++);
            hr.setHeightInPoints(32);
            hr.createCell(0).setCellValue("STT");
            hr.getCell(0).setCellStyle(header);
            for (int i = 0; i < n; i++) {
                Cell c = hr.createCell(i + 1);
                c.setCellValue(t.columns().get(i).title());
                c.setCellStyle(header);
            }
            int stt = 1;
            for (Map<String, Object> row : t.rows()) {
                Row xr = sh.createRow(r++);
                Cell sc = xr.createCell(0);
                sc.setCellValue(stt++);
                sc.setCellStyle(num);
                for (int i = 0; i < n; i++) write(xr.createCell(i + 1), t.columns().get(i), row.get(t.columns().get(i).key()), text, num, pct);
            }
            if (t.totals() != null && !t.totals().isEmpty()) {
                Row xr = sh.createRow(r);
                xr.createCell(0).setCellStyle(totalText);
                for (int i = 0; i < n; i++) {
                    Column col = t.columns().get(i);
                    Object v = t.totals().get(col.key());
                    write(xr.createCell(i + 1), col, v, totalText, totalNum, totalPct);
                }
            }
            sh.setColumnWidth(0, 6 * 256);
            for (int i = 0; i < n; i++) {
                Column col = t.columns().get(i);
                int w = switch (col.type()) {
                    case "money" -> 18;
                    case "int", "percent" -> 13;
                    case "date" -> 13;
                    default -> col.key().equals("name") ? 36 : 16;
                };
                sh.setColumnWidth(i + 1, w * 256);
            }
            sh.createFreezePane(0, 6);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void border(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private static void write(Cell c, Column col, Object v, CellStyle text, CellStyle num, CellStyle pct) {
        if (v == null) {
            c.setCellStyle(text);
            return;
        }
        switch (col.type()) {
            case "int", "money" -> { c.setCellValue(toDouble(v)); c.setCellStyle(num); }
            case "percent" -> { c.setCellValue(toDouble(v)); c.setCellStyle(pct); }
            case "date" -> { c.setCellValue(formatDate(v)); c.setCellStyle(text); }
            default -> { c.setCellValue(String.valueOf(v)); c.setCellStyle(text); }
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof BigDecimal b) return b.doubleValue();
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return 0; }
    }

    private static String formatDate(Object v) {
        try {
            return LocalDate.parse(String.valueOf(v)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return String.valueOf(v);
        }
    }

    // ---------------- PDF ----------------
    public byte[] toPdf(ReportTable t) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            Document doc = new Document(pdf, PageSize.A4.rotate());
            doc.setMargins(28, 24, 28, 24);
            PdfFont font = loadFont("fonts/DejaVuSans.ttf");
            PdfFont fontBold = loadFont("fonts/DejaVuSans-Bold.ttf");
            doc.setFont(font).setFontSize(8.5f);

            doc.add(new Paragraph(ORG).setFont(fontBold).setFontSize(9));
            doc.add(new Paragraph(t.title().toUpperCase(new Locale("vi"))).setFont(fontBold).setFontSize(15)
                    .setTextAlignment(TextAlignment.CENTER).setMarginTop(6));
            doc.add(new Paragraph(t.subtitle()).setTextAlignment(TextAlignment.CENTER).setFontSize(9));
            doc.add(new Paragraph("Ngày xuất: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))
                    .setTextAlignment(TextAlignment.RIGHT).setFontSize(8).setMarginBottom(6));

            int n = t.columns().size();
            float[] widths = new float[n + 1];
            widths[0] = 3;
            for (int i = 0; i < n; i++) {
                Column c = t.columns().get(i);
                widths[i + 1] = switch (c.type()) {
                    case "money" -> 10;
                    case "int", "percent" -> 7;
                    case "date" -> 8;
                    default -> c.key().equals("name") ? 20 : 9;
                };
            }
            Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
            DeviceRgb headBg = new DeviceRgb(30, 58, 138);
            DeviceRgb zebra = new DeviceRgb(245, 247, 250);
            DeviceRgb totalBg = new DeviceRgb(226, 232, 240);
            table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph("STT")).setFont(fontBold).setFontColor(DeviceRgb.WHITE).setBackgroundColor(headBg)
                    .setTextAlignment(TextAlignment.CENTER));
            for (Column c : t.columns()) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(c.title())).setFont(fontBold).setFontColor(DeviceRgb.WHITE)
                        .setBackgroundColor(headBg).setTextAlignment(TextAlignment.CENTER));
            }
            NumberFormat nf = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
            nf.setMaximumFractionDigits(0);
            int stt = 1;
            for (Map<String, Object> row : t.rows()) {
                boolean odd = stt % 2 == 0;
                com.itextpdf.layout.element.Cell sc = new com.itextpdf.layout.element.Cell().add(new Paragraph(String.valueOf(stt++))).setTextAlignment(TextAlignment.CENTER);
                if (odd) sc.setBackgroundColor(zebra);
                table.addCell(sc);
                for (Column c : t.columns()) {
                    com.itextpdf.layout.element.Cell cell = pdfCell(c, row.get(c.key()), nf);
                    if (odd) cell.setBackgroundColor(zebra);
                    table.addCell(cell);
                }
            }
            if (t.totals() != null && !t.totals().isEmpty()) {
                table.addCell(new com.itextpdf.layout.element.Cell().setBackgroundColor(totalBg));
                for (Column c : t.columns()) {
                    table.addCell(pdfCell(c, t.totals().get(c.key()), nf).setFont(fontBold).setBackgroundColor(totalBg));
                }
            }
            if (t.rows().isEmpty()) {
                doc.add(new Paragraph("Không có dữ liệu trong khoảng thời gian đã chọn.").setTextAlignment(TextAlignment.CENTER));
            } else {
                doc.add(table);
            }
            doc.add(new Paragraph("Người lập báo cáo").setTextAlignment(TextAlignment.RIGHT).setMarginTop(24).setMarginRight(40).setFont(fontBold));
            doc.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static com.itextpdf.layout.element.Cell pdfCell(Column c, Object v, NumberFormat nf) {
        String s;
        TextAlignment align = TextAlignment.LEFT;
        if (v == null) s = "";
        else switch (c.type()) {
            case "int", "money" -> { s = nf.format(toDouble(v)); align = TextAlignment.RIGHT; }
            case "percent" -> { s = String.format(new Locale("vi", "VN"), "%.1f%%", toDouble(v)); align = TextAlignment.RIGHT; }
            case "date" -> { s = formatDate(v); align = TextAlignment.CENTER; }
            default -> s = String.valueOf(v);
        }
        return new com.itextpdf.layout.element.Cell().add(new Paragraph(s)).setTextAlignment(align).setPadding(3);
    }

    private static PdfFont loadFont(String path) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return PdfFontFactory.createFont(in.readAllBytes(), PdfEncodings.IDENTITY_H,
                    PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        }
    }
}
