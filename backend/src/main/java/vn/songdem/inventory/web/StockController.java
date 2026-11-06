package vn.songdem.inventory.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.dto.CommonDtos.ReasonRequest;
import vn.songdem.inventory.dto.StockDtos.*;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.ReceiptService;
import vn.songdem.inventory.service.ReceiptService.ReceiptFilter;
import vn.songdem.inventory.service.SalesService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StockController {
    private final ReceiptService receiptService;
    private final SalesService salesService;
    private final CurrentUser currentUser;

    // ----- Phiếu nhập -----
    @GetMapping("/receipts")
    public List<ReceiptDto> receipts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) String supplierId, @RequestParam(required = false) String itemId,
                                     @RequestParam(required = false) String status, @RequestParam(required = false) String debtStatus,
                                     @RequestParam(required = false) String q, @RequestParam(required = false) Boolean openDebt) {
        boolean values = currentUser.canSeeValues();
        return receiptService.list(new ReceiptFilter(from, to, supplierId, itemId, status, values ? debtStatus : null, q,
                values ? openDebt : null), values);
    }

    @GetMapping("/receipts/{id}")
    public ReceiptDto receipt(@PathVariable String id) {
        return receiptService.get(id, currentUser.canSeeValues());
    }

    @GetMapping("/receipts/check-invoice")
    public Map<String, Object> checkInvoice(@RequestParam String supplierId, @RequestParam String invoiceNo,
                                            @RequestParam(required = false) String excludeId) {
        var dup = receiptService.findDuplicateInvoice(supplierId, invoiceNo, excludeId);
        return dup.<Map<String, Object>>map(code -> Map.of("duplicate", true, "receiptCode", code))
                .orElse(Map.of("duplicate", false));
    }

    @PostMapping(value = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public ReceiptDto createReceipt(@Valid @RequestPart("data") ReceiptRequest req,
                                    @RequestPart(value = "invoiceFile", required = false) MultipartFile invoiceFile) {
        return receiptService.create(req, invoiceFile, currentUser.get());
    }

    @PutMapping("/receipts/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ReceiptDto updateReceipt(@PathVariable String id, @Valid @RequestBody ReceiptRequest req) {
        return receiptService.update(id, req, currentUser.get());
    }

    @PostMapping("/receipts/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public ReceiptDto cancelReceipt(@PathVariable String id, @RequestBody ReasonRequest req) {
        return receiptService.cancel(id, req.reason(), currentUser.get());
    }

    // ----- Doanh số POS / phiếu xuất bán -----
    @GetMapping("/sales")
    public List<SalesDto> sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) String status, @RequestParam(required = false) String itemId) {
        return salesService.list(from, to, status, itemId);
    }

    @GetMapping("/sales/{id}")
    public SalesDto salesDetail(@PathVariable String id) {
        return salesService.get(id);
    }

    @PostMapping(value = "/sales/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public SalesPreview preview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salesDate,
                                @RequestPart("file") MultipartFile file) {
        return salesService.preview(salesDate, file);
    }

    @PostMapping(value = "/sales", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public SalesDto importSales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate salesDate,
                                @RequestPart("file") MultipartFile file) {
        return salesService.importSales(salesDate, file, currentUser.get());
    }

    @PutMapping("/sales/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public SalesDto updateSales(@PathVariable String id, @Valid @RequestBody SalesUpdateRequest req) {
        return salesService.update(id, req, currentUser.get());
    }

    @PostMapping("/sales/{id}/cancel")
    @PreAuthorize("hasRole('MANAGER')")
    public SalesDto cancelSales(@PathVariable String id, @RequestBody ReasonRequest req) {
        return salesService.cancel(id, req.reason(), currentUser.get());
    }
}
