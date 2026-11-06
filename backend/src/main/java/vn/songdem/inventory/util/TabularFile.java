package vn.songdem.inventory.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import vn.songdem.inventory.web.ApiException;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Đọc file bảng tính (xlsx, xls, csv) thành danh sách dòng có tiêu đề cột. */
public class TabularFile {

    public record Row(int rowNumber, Map<String, String> values) {
        public String get(String header) {
            if (header == null) return null;
            String v = values.get(header);
            return v == null ? null : v.trim();
        }
    }

    private final List<String> headers;
    private final List<Row> rows;

    private TabularFile(List<String> headers, List<Row> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    public List<String> headers() { return headers; }
    public List<Row> rows() { return rows; }

    public static TabularFile read(String filename, byte[] data) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) return readCsv(data);
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) return readExcel(data);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Không đọc được file \"" + filename + "\". File có thể bị hỏng hoặc sai định dạng.");
        }
        throw ApiException.badRequest("File sai định dạng. Chỉ chấp nhận file Excel (.xlsx, .xls) hoặc CSV (.csv).");
    }

    private static TabularFile readCsv(byte[] data) throws Exception {
        String text = new String(data, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) text = text.substring(1);
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.chars().filter(c -> c == ';').count() > firstLine.chars().filter(c -> c == ',').count() ? ';' : ',';
        List<String> headers = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        try (Reader reader = new java.io.StringReader(text);
             CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).build().parse(reader)) {
            boolean headerDone = false;
            for (CSVRecord rec : parser) {
                List<String> cells = new ArrayList<>();
                rec.forEach(cells::add);
                if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
                if (!headerDone) {
                    for (String c : cells) headers.add(TextUtil.normalize(c));
                    headerDone = true;
                    continue;
                }
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < cells.size(); i++) m.put(headers.get(i), cells.get(i));
                rows.add(new Row((int) rec.getRecordNumber(), m));
            }
        }
        if (headers.isEmpty()) throw ApiException.badRequest("File không có dữ liệu");
        return new TabularFile(headers, rows);
    }

    private static TabularFile readExcel(byte[] data) throws Exception {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter(new Locale("vi", "VN"));
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
            List<String> headers = null;
            List<Row> rows = new ArrayList<>();
            for (org.apache.poi.ss.usermodel.Row r : sheet) {
                if (r == null) continue;
                int last = Math.max(r.getLastCellNum(), 0);
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < last; i++) cells.add(cellText(r.getCell(i), fmt, evaluator));
                if (cells.stream().allMatch(c -> c == null || c.isBlank())) continue;
                if (headers == null) {
                    headers = new ArrayList<>();
                    for (String c : cells) headers.add(TextUtil.normalize(c));
                    continue;
                }
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < headers.size() && i < cells.size(); i++) m.put(headers.get(i), cells.get(i));
                rows.add(new Row(r.getRowNum() + 1, m));
            }
            if (headers == null) throw ApiException.badRequest("File không có dữ liệu");
            return new TabularFile(headers, rows);
        }
    }

    private static String cellText(Cell cell, DataFormatter fmt, FormulaEvaluator ev) {
        if (cell == null) return "";
        CellType type = cell.getCellType() == CellType.FORMULA ? ev.evaluateFormulaCell(cell) : cell.getCellType();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                LocalDate d = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return d.toString();
            }
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.STRING) return cell.getStringCellValue();
        if (type == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        return fmt.formatCellValue(cell);
    }

    /**
     * Xác định cột theo danh sách tên gọi khác nhau. Trả về tên cột (đã chuẩn hóa) hoặc null nếu không có.
     */
    public String findColumn(String... aliases) {
        for (String a : aliases) {
            String n = TextUtil.normalize(a);
            if (headers.contains(n)) return n;
        }
        return null;
    }

    /** Kiểm tra các cột bắt buộc, ném lỗi liệt kê cột thiếu. */
    public Map<String, String> requireColumns(LinkedHashMap<String, String[]> spec) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (var e : spec.entrySet()) {
            String col = findColumn(e.getValue());
            if (col == null) missing.add(e.getValue()[0]);
            else result.put(e.getKey(), col);
        }
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("File thiếu cột bắt buộc: " + String.join(", ", missing),
                    missing.stream().map(m -> "Thiếu cột \"" + m + "\"").toList());
        }
        return result;
    }

    public static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        try {
            if (s.matches("\\d{4}-\\d{1,2}-\\d{1,2}.*")) return LocalDate.parse(s.substring(0, 10));
            String[] p = s.split("[/\\-.]");
            if (p.length == 3) {
                int d = Integer.parseInt(p[0].trim()), m = Integer.parseInt(p[1].trim()), y = Integer.parseInt(p[2].trim().substring(0, 4));
                return LocalDate.of(y, m, d);
            }
        } catch (Exception ignored) { }
        throw new IllegalArgumentException(raw);
    }

    /** Dùng khi cần đọc nhanh file CSV từ InputStream (ít dùng). */
    static Reader utf8(byte[] data) {
        return new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8);
    }
}
