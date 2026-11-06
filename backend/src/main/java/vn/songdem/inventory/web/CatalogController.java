package vn.songdem.inventory.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.dto.CatalogDtos.*;
import vn.songdem.inventory.dto.CommonDtos.StatusRequest;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.CatalogService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService catalogService;
    private final CurrentUser currentUser;

    // ----- Loại hàng -----
    @GetMapping("/categories")
    public List<CategoryDto> categories(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active) {
        return catalogService.listCategories(q, active);
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryDto createCategory(@Valid @RequestBody CategoryRequest req) {
        return catalogService.createCategory(req, currentUser.get());
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public CategoryDto updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest req) {
        return catalogService.updateCategory(id, req, currentUser.get());
    }

    @PatchMapping("/categories/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public void categoryStatus(@PathVariable String id, @RequestBody StatusRequest req) {
        catalogService.setCategoryActive(id, Boolean.TRUE.equals(req.active()), currentUser.get());
    }

    // ----- Nhà cung cấp -----
    @GetMapping("/suppliers")
    public List<SupplierDto> suppliers(@RequestParam(required = false) String q, @RequestParam(required = false) Boolean active) {
        return catalogService.listSuppliers(q, active, currentUser.canSeeValues());
    }

    @GetMapping("/suppliers/{id}")
    public SupplierDto supplier(@PathVariable String id) {
        return catalogService.getSupplier(id, currentUser.canSeeValues());
    }

    @PostMapping(value = "/suppliers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public SupplierDto createSupplier(@Valid @RequestPart("data") SupplierRequest req,
                                      @RequestPart(value = "contractFile", required = false) MultipartFile file) {
        return catalogService.createSupplier(req, file, currentUser.get());
    }

    @PutMapping(value = "/suppliers/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MANAGER')")
    public SupplierDto updateSupplier(@PathVariable String id, @Valid @RequestPart("data") SupplierRequest req,
                                      @RequestPart(value = "contractFile", required = false) MultipartFile file) {
        return catalogService.updateSupplier(id, req, file, currentUser.get());
    }

    @PatchMapping("/suppliers/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public void supplierStatus(@PathVariable String id, @RequestBody StatusRequest req) {
        catalogService.setSupplierActive(id, Boolean.TRUE.equals(req.active()), currentUser.get());
    }

    // ----- Mặt hàng -----
    @GetMapping("/items")
    public List<ItemDto> items(@RequestParam(required = false) String q, @RequestParam(required = false) String categoryId,
                               @RequestParam(required = false) String supplierId, @RequestParam(required = false) Boolean active,
                               @RequestParam(required = false) String stockStatus) {
        return catalogService.listItems(q, categoryId, supplierId, active, stockStatus, currentUser.canSeeValues());
    }

    @GetMapping("/items/{id}")
    public ItemDto item(@PathVariable String id) {
        return catalogService.getItem(id, currentUser.canSeeValues());
    }

    @GetMapping("/items/{id}/movements")
    public List<MovementDto> movements(@PathVariable String id) {
        return catalogService.movements(id);
    }

    @PostMapping("/items")
    @PreAuthorize("hasRole('MANAGER')")
    public ItemDto createItem(@Valid @RequestBody ItemRequest req) {
        return catalogService.createItem(req, currentUser.get());
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ItemDto updateItem(@PathVariable String id, @Valid @RequestBody ItemRequest req) {
        return catalogService.updateItem(id, req, currentUser.get());
    }

    @PatchMapping("/items/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    public ItemDto itemStatus(@PathVariable String id, @RequestBody StatusRequest req) {
        return catalogService.setItemActive(id, Boolean.TRUE.equals(req.active()), Boolean.TRUE.equals(req.confirm()), currentUser.get());
    }

    @PatchMapping("/items/{id}/threshold")
    @PreAuthorize("hasRole('MANAGER')")
    public ItemDto threshold(@PathVariable String id, @Valid @RequestBody ThresholdRequest req) {
        return catalogService.updateThreshold(id, req, currentUser.get());
    }
}
