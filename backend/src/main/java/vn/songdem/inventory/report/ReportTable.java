package vn.songdem.inventory.report;

import java.util.List;
import java.util.Map;

/** Cấu trúc báo cáo dạng bảng dùng chung cho hiển thị, xuất Excel và PDF. */
public record ReportTable(String key, String title, String subtitle, List<Column> columns,
                          List<Map<String, Object>> rows, Map<String, Object> totals, Map<String, Object> extra) {

    /** type: text | int | money | percent | date */
    public record Column(String key, String title, String type) {}
}
