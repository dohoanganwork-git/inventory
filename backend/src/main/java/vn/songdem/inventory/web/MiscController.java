package vn.songdem.inventory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.songdem.inventory.report.ReportExporter;
import vn.songdem.inventory.report.ReportService;
import vn.songdem.inventory.report.ReportTable;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MiscController {
    private final FileStorageService fileStorageService;
    private final NotificationService notificationService;
    private final ReportService reportService;
    private final ReportExporter reportExporter;
    private final TemplateService templateService;
    private final DashboardService dashboardService;
    private final CurrentUser currentUser;

    @GetMapping("/files/{id}")
    public ResponseEntity<byte[]> file(@PathVariable String id, @RequestParam(defaultValue = "false") boolean inline) {
        var f = fileStorageService.load(id);
        return Downloads.file(f.file().getOriginalName(), f.file().getContentType(), f.data(), inline);
    }

    @GetMapping("/templates/{type}")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        var t = templateService.build(type);
        return Downloads.file(t.fileName(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", t.data(), false);
    }

    @GetMapping("/dashboard")
    public DashboardService.DashboardDto dashboard() {
        return dashboardService.get(currentUser.role());
    }

    // ----- Thông báo -----
    @GetMapping("/notifications")
    public Map<String, Object> notifications(@RequestParam(defaultValue = "30") int limit) {
        String uid = currentUser.principal().id();
        return Map.of("items", notificationService.list(uid, Math.min(limit, 100)), "unread", notificationService.unread(uid));
    }

    @GetMapping("/notifications/unread-count")
    public Map<String, Long> unread() {
        return Map.of("unread", notificationService.unread(currentUser.principal().id()));
    }

    @PostMapping("/notifications/{id}/read")
    public void read(@PathVariable String id) {
        notificationService.markRead(currentUser.principal().id(), id);
    }

    @PostMapping("/notifications/read-all")
    public void readAll() {
        notificationService.markAllRead(currentUser.principal().id());
    }

    // ----- Báo cáo -----
    @GetMapping("/reports")
    public List<Map<String, String>> reportTypes() {
        boolean values = currentUser.canSeeValues();
        List<Map<String, String>> out = new ArrayList<>();
        ReportService.TYPES.forEach((k, v) -> {
            if (values || !k.equals("cong-no")) out.add(Map.of("key", k, "title", v));
        });
        return out;
    }

    @GetMapping("/reports/{type}")
    public ReportTable report(@PathVariable String type,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.build(type, from, to, currentUser.role());
    }

    @GetMapping("/reports/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type, @RequestParam(defaultValue = "xlsx") String format,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportTable t = reportService.build(type, from, to, currentUser.role());
        String stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if ("pdf".equalsIgnoreCase(format)) {
            return Downloads.file("bao-cao-" + type + "-" + stamp + ".pdf", "application/pdf", reportExporter.toPdf(t), false);
        }
        return Downloads.file("bao-cao-" + type + "-" + stamp + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", reportExporter.toExcel(t), false);
    }
}
