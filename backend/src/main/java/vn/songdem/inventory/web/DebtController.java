package vn.songdem.inventory.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.dto.CommonDtos.ReasonRequest;
import vn.songdem.inventory.dto.DebtDtos.*;
import vn.songdem.inventory.dto.StockDtos.PaymentDto;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.DebtReconciliationService;
import vn.songdem.inventory.service.PaymentService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DebtController {
    private final PaymentService paymentService;
    private final DebtReconciliationService reconService;
    private final CurrentUser currentUser;

    @GetMapping("/debts/summary")
    @PreAuthorize("hasAnyRole('MANAGER','ACCOUNTANT')")
    public List<SupplierDebtDto> summary() {
        return paymentService.summary();
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('MANAGER','ACCOUNTANT')")
    public List<PaymentDto> payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return paymentService.list(from, to);
    }

    @PostMapping(value = "/payments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public List<PaymentDto> pay(@Valid @RequestPart("data") PaymentRequest req,
                                @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return paymentService.create(req, evidence, currentUser.get());
    }

    @PutMapping(value = "/payments/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public PaymentDto updatePayment(@PathVariable String id, @Valid @RequestPart("data") PaymentUpdateRequest req,
                                    @RequestPart(value = "evidence", required = false) MultipartFile evidence) {
        return paymentService.update(id, req, evidence, currentUser.get());
    }

    @PostMapping("/payments/{id}/cancel")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public PaymentDto cancelPayment(@PathVariable String id, @RequestBody ReasonRequest req) {
        return paymentService.cancel(id, req.reason(), currentUser.get());
    }

    // ----- Đối soát công nợ -----
    @GetMapping("/debt-reconciliations")
    @PreAuthorize("hasAnyRole('MANAGER','ACCOUNTANT')")
    public List<DebtReconDto> recons() {
        return reconService.list();
    }

    @GetMapping("/debt-reconciliations/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','ACCOUNTANT')")
    public DebtReconDto recon(@PathVariable String id) {
        return reconService.get(id);
    }

    @PostMapping(value = "/debt-reconciliations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public DebtReconDto createRecon(@RequestParam String supplierId,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
                                    @RequestPart("invoiceFile") MultipartFile invoiceFile,
                                    @RequestPart(value = "evidenceFile", required = false) MultipartFile evidenceFile) {
        return reconService.create(supplierId, periodFrom, periodTo, invoiceFile, evidenceFile, currentUser.get());
    }

    @PostMapping(value = "/debt-reconciliations/{id}/reupload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public DebtReconDto reupload(@PathVariable String id, @RequestPart("invoiceFile") MultipartFile invoiceFile,
                                 @RequestPart(value = "evidenceFile", required = false) MultipartFile evidenceFile) {
        return reconService.reupload(id, invoiceFile, evidenceFile, currentUser.get());
    }

    @PostMapping("/debt-reconciliations/{id}/rematch")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public DebtReconDto rematch(@PathVariable String id) {
        return reconService.rematch(id, currentUser.get());
    }

    @PatchMapping("/debt-reconciliations/{id}/lines/{lineId}")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public DebtReconDto lineNote(@PathVariable String id, @PathVariable String lineId, @Valid @RequestBody LineNoteRequest req) {
        return reconService.updateLineNote(id, lineId, req.note(), currentUser.get());
    }

    @PostMapping("/debt-reconciliations/{id}/complete")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public DebtReconDto complete(@PathVariable String id, @Valid @RequestBody CompleteRequest req) {
        return reconService.complete(id, req.note(), currentUser.get());
    }

    @PostMapping("/debt-reconciliations/{id}/reopen")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public DebtReconDto reopen(@PathVariable String id, @RequestBody ReasonRequest req) {
        return reconService.reopen(id, req.reason(), currentUser.get());
    }
}
