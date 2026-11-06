package vn.songdem.inventory.web;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.dto.StocktakeDtos.*;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.AlertService;
import vn.songdem.inventory.service.StocktakeService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StocktakeController {
    private final StocktakeService stocktakeService;
    private final AlertService alertService;
    private final CurrentUser currentUser;

    @GetMapping("/stocktakes")
    public List<StocktakeDto> list() {
        return stocktakeService.list();
    }

    @GetMapping("/stocktakes/{id}")
    public StocktakeDto get(@PathVariable String id) {
        return stocktakeService.get(id);
    }

    @PostMapping(value = "/stocktakes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public StocktakeDto create(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate stocktakeDate,
                               @RequestPart("countFile") MultipartFile countFile,
                               @RequestPart(value = "damageFile", required = false) MultipartFile damageFile) {
        return stocktakeService.create(stocktakeDate, countFile, damageFile, currentUser.get());
    }

    @PostMapping(value = "/stocktakes/{id}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public StocktakeDto resubmit(@PathVariable String id, @RequestPart("countFile") MultipartFile countFile,
                                 @RequestPart(value = "damageFile", required = false) MultipartFile damageFile) {
        return stocktakeService.resubmit(id, countFile, damageFile, currentUser.get());
    }

    @PostMapping("/stocktakes/{id}/decision")
    @PreAuthorize("hasRole('MANAGER')")
    public StocktakeDto decide(@PathVariable String id, @RequestBody DecisionRequest req) {
        return stocktakeService.decide(id, req, currentUser.get());
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('STAFF','MANAGER')")
    public AlertService.AlertsDto alerts() {
        return alertService.alerts(currentUser.canSeeValues());
    }
}
