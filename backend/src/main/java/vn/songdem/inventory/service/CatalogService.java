package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.songdem.inventory.domain.*;
import vn.songdem.inventory.dto.CatalogDtos.*;
import vn.songdem.inventory.dto.CommonDtos.FileDto;
import vn.songdem.inventory.dto.CommonDtos.RefDto;
import vn.songdem.inventory.repository.*;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** UC_07, UC_08, UC_09, UC_24 – Quản lý danh mục loại hàng, mặt hàng, nhà cung cấp. */
@Service
@RequiredArgsConstructor
public class CatalogService {
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ItemRepository itemRepository;
    private final GoodsReceiptRepository receiptRepository;
    private final StockMovementRepository movementRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;
    private final ModelMapper modelMapper;

    // ===================== Loại hàng =====================

    @Transactional(readOnly = true)
    public List<CategoryDto> listCategories(String q, Boolean active) {
        Map<String, Long> counts = itemRepository.findAll().stream()
                .collect(Collectors.groupingBy(i -> i.getCategory().getId(), Collectors.counting()));
        String nq = TextUtil.normalize(q);
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(c -> active == null || c.isActive() == active)
                .filter(c -> nq.isEmpty() || TextUtil.normalize(c.getName() + " " + c.getCode()).contains(nq))
                .map(c -> new CategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.isActive(),
                        counts.getOrDefault(c.getId(), 0L), c.getCreatedAt()))
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(CategoryRequest req, User actor) {
        if (categoryRepository.existsByCodeIgnoreCase(req.code().trim())) {
            throw ApiException.conflict("DUPLICATE_CODE", "Mã loại hàng \"" + req.code() + "\" đã tồn tại");
        }
        Category c = new Category();
        modelMapper.map(req, c);
        c.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        c.setName(req.name().trim());
        categoryRepository.save(c);
        auditService.log("CATEGORY", c.getId(), c.getCode(), "CREATE", null, c.getName(), actor);
        return new CategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription(), true, 0, c.getCreatedAt());
    }

    @Transactional
    public CategoryDto updateCategory(String id, CategoryRequest req, User actor) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy loại hàng"));
        categoryRepository.findByCodeIgnoreCase(req.code().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("DUPLICATE_CODE", "Mã loại hàng \"" + req.code() + "\" đã tồn tại");
        });
        c.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        c.setName(req.name().trim());
        c.setDescription(TextUtil.trimToNull(req.description()));
        auditService.log("CATEGORY", c.getId(), c.getCode(), "UPDATE", null, null, actor);
        return new CategoryDto(c.getId(), c.getCode(), c.getName(), c.getDescription(), c.isActive(),
                itemRepository.countByCategoryId(id), c.getCreatedAt());
    }

    @Transactional
    public void setCategoryActive(String id, boolean active, User actor) {
        Category c = categoryRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy loại hàng"));
        c.setActive(active);
        auditService.log("CATEGORY", c.getId(), c.getCode(), active ? "ACTIVATE" : "DEACTIVATE", null, null, actor);
    }

    // ===================== Nhà cung cấp =====================

    public static String contractStatus(Supplier s) {
        if (s.getContractEnd() == null) return s.getContractNo() == null ? "NONE" : "ACTIVE";
        LocalDate today = LocalDate.now();
        if (s.getContractEnd().isBefore(today)) return "EXPIRED";
        if (!s.getContractEnd().isAfter(today.plusDays(30))) return "EXPIRING";
        return "ACTIVE";
    }

    private SupplierDto toDto(Supplier s, long itemCount, BigDecimal openDebt) {
        return new SupplierDto(s.getId(), s.getCode(), s.getName(), s.getContactName(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.getTaxCode(), s.getPaymentMethod(), s.getBankAccount(), s.getContractNo(),
                s.getContractStart(), s.getContractEnd(), s.getContractNote(), contractStatus(s),
                FileDto.of(s.getContractFile()), s.isActive(), itemCount, openDebt, s.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<SupplierDto> listSuppliers(String q, Boolean active, boolean withValues) {
        Map<String, Long> counts = itemRepository.findAll().stream().filter(i -> i.getSupplier() != null)
                .collect(Collectors.groupingBy(i -> i.getSupplier().getId(), Collectors.counting()));
        Map<String, BigDecimal> debts = new HashMap<>();
        if (withValues) {
            for (GoodsReceipt r : receiptRepository.findOpenDebts()) {
                debts.merge(r.getSupplier().getId(), r.getRemainingAmount(), BigDecimal::add);
            }
        }
        String nq = TextUtil.normalize(q);
        return supplierRepository.findAllByOrderByNameAsc().stream()
                .filter(s -> active == null || s.isActive() == active)
                .filter(s -> nq.isEmpty() || TextUtil.normalize(String.join(" ", s.getName(), s.getCode(),
                        Objects.toString(s.getContactName(), ""), Objects.toString(s.getPhone(), ""),
                        Objects.toString(s.getContractNo(), ""))).contains(nq))
                .map(s -> toDto(s, counts.getOrDefault(s.getId(), 0L), withValues ? debts.getOrDefault(s.getId(), BigDecimal.ZERO) : null))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierDto getSupplier(String id, boolean withValues) {
        Supplier s = supplierRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        BigDecimal debt = null;
        if (withValues) {
            debt = receiptRepository.findOpenDebts().stream().filter(r -> r.getSupplier().getId().equals(id))
                    .map(GoodsReceipt::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return toDto(s, itemRepository.countBySupplierId(id), debt);
    }

    private void applySupplier(Supplier s, SupplierRequest req) {
        if (req.contractStart() != null && req.contractEnd() != null && req.contractEnd().isBefore(req.contractStart())) {
            throw ApiException.badRequest("Ngày kết thúc hợp đồng phải sau ngày bắt đầu");
        }
        s.setCode(req.code().trim().toUpperCase(Locale.ROOT));
        s.setName(req.name().trim());
        s.setContactName(TextUtil.trimToNull(req.contactName()));
        s.setPhone(TextUtil.trimToNull(req.phone()));
        s.setEmail(TextUtil.trimToNull(req.email()));
        s.setAddress(TextUtil.trimToNull(req.address()));
        s.setTaxCode(TextUtil.trimToNull(req.taxCode()));
        s.setPaymentMethod(TextUtil.trimToNull(req.paymentMethod()));
        s.setBankAccount(TextUtil.trimToNull(req.bankAccount()));
        s.setContractNo(TextUtil.trimToNull(req.contractNo()));
        s.setContractStart(req.contractStart());
        s.setContractEnd(req.contractEnd());
        s.setContractNote(TextUtil.trimToNull(req.contractNote()));
    }

    @Transactional
    public SupplierDto createSupplier(SupplierRequest req, MultipartFile contractFile, User actor) {
        if (supplierRepository.existsByCodeIgnoreCase(req.code().trim())) {
            throw ApiException.conflict("DUPLICATE_CODE", "Mã nhà cung cấp \"" + req.code() + "\" đã tồn tại");
        }
        Supplier s = new Supplier();
        applySupplier(s, req);
        if (contractFile != null && !contractFile.isEmpty()) {
            s.setContractFile(fileStorageService.store(contractFile, FileStorageService.DOCUMENT_EXT, "Tệp hợp đồng", "CONTRACT", actor));
        }
        supplierRepository.save(s);
        auditService.log("SUPPLIER", s.getId(), s.getCode(), "CREATE", null, s.getName(), actor);
        return toDto(s, 0, BigDecimal.ZERO);
    }

    @Transactional
    public SupplierDto updateSupplier(String id, SupplierRequest req, MultipartFile contractFile, User actor) {
        Supplier s = supplierRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        supplierRepository.findByCodeIgnoreCase(req.code().trim()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("DUPLICATE_CODE", "Mã nhà cung cấp \"" + req.code() + "\" đã tồn tại");
        });
        applySupplier(s, req);
        if (contractFile != null && !contractFile.isEmpty()) {
            s.setContractFile(fileStorageService.store(contractFile, FileStorageService.DOCUMENT_EXT, "Tệp hợp đồng", "CONTRACT", actor));
        }
        auditService.log("SUPPLIER", s.getId(), s.getCode(), "UPDATE", null, null, actor);
        return toDto(s, itemRepository.countBySupplierId(id), null);
    }

    @Transactional
    public void setSupplierActive(String id, boolean active, User actor) {
        Supplier s = supplierRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp"));
        s.setActive(active);
        auditService.log("SUPPLIER", s.getId(), s.getCode(), active ? "ACTIVATE" : "DEACTIVATE", null, null, actor);
    }

    // ===================== Mặt hàng =====================

    public ItemDto toDto(Item i, boolean withValues) {
        BigDecimal cost = i.getLastCost() == null ? BigDecimal.ZERO : i.getLastCost();
        return new ItemDto(i.getId(), i.getCode(), i.getPosCode(), i.getName(), i.getUnit(),
                RefDto.of(i.getCategory()), RefDto.of(i.getSupplier()), i.getMinStock(), i.getMaxStock(),
                i.getCurrentStock(), withValues ? cost : null, i.getSalePrice(),
                withValues ? cost.multiply(BigDecimal.valueOf(i.getCurrentStock())) : null,
                i.stockStatus(), i.isActive(), i.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ItemDto> listItems(String q, String categoryId, String supplierId, Boolean active, String stockStatus, boolean withValues) {
        String nq = TextUtil.normalize(q);
        return itemRepository.findAllWithRefs().stream()
                .filter(i -> active == null || i.isActive() == active)
                .filter(i -> categoryId == null || categoryId.isBlank() || i.getCategory().getId().equals(categoryId))
                .filter(i -> supplierId == null || supplierId.isBlank() || (i.getSupplier() != null && i.getSupplier().getId().equals(supplierId)))
                .filter(i -> stockStatus == null || stockStatus.isBlank() || i.stockStatus().equals(stockStatus))
                .filter(i -> nq.isEmpty() || TextUtil.normalize(i.getName() + " " + i.getCode() + " " + i.getPosCode()).contains(nq))
                .map(i -> toDto(i, withValues))
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemDto getItem(String id, boolean withValues) {
        return toDto(findItem(id), withValues);
    }

    @Transactional(readOnly = true)
    public List<MovementDto> movements(String itemId) {
        return movementRepository.findByItem(itemId).stream()
                .map(m -> new MovementDto(m.getId(), m.getType().name(), m.getQuantityChange(), m.getStockBefore(),
                        m.getStockAfter(), m.getMovementDate(), m.getRefType(), m.getRefId(), m.getRefCode(), m.getNote(),
                        m.getCreatedBy() == null ? null : m.getCreatedBy().getFullName(), m.getCreatedAt()))
                .toList();
    }

    public Item findItem(String id) {
        return itemRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy mặt hàng"));
    }

    private void applyItem(Item i, ItemRequest req, String selfId) {
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        String pos = req.posCode().trim();
        itemRepository.findByCodeIgnoreCase(code).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
            throw ApiException.conflict("DUPLICATE_CODE", "Mã hàng \"" + code + "\" đã tồn tại");
        });
        itemRepository.findByPosCodeIgnoreCase(pos).filter(o -> !o.getId().equals(selfId)).ifPresent(o -> {
            throw ApiException.conflict("DUPLICATE_POS_CODE", "Mã POS \"" + pos + "\" đã được dùng cho mặt hàng \"" + o.getName() + "\"");
        });
        Category c = categoryRepository.findById(req.categoryId())
                .orElseThrow(() -> ApiException.badRequest("Loại hàng chưa tồn tại, vui lòng thêm loại hàng trước"));
        if (!c.isActive() && (i.getCategory() == null || !i.getCategory().getId().equals(c.getId()))) {
            throw ApiException.badRequest("Loại hàng \"" + c.getName() + "\" đã ngừng sử dụng");
        }
        Supplier s = null;
        if (req.supplierId() != null && !req.supplierId().isBlank()) {
            s = supplierRepository.findById(req.supplierId())
                    .orElseThrow(() -> ApiException.badRequest("Nhà cung cấp chưa tồn tại, vui lòng thêm nhà cung cấp trước"));
        }
        if (req.maxStock() != null && req.maxStock() > 0 && req.maxStock() <= req.minStock()) {
            throw ApiException.badRequest("Mức tồn tối đa phải lớn hơn mốc cảnh báo tồn thấp");
        }
        i.setCode(code);
        i.setPosCode(pos);
        i.setName(req.name().trim());
        i.setUnit(req.unit().trim());
        i.setCategory(c);
        i.setSupplier(s);
        i.setMinStock(req.minStock());
        i.setMaxStock(req.maxStock() == null || req.maxStock() == 0 ? null : req.maxStock());
        i.setSalePrice(req.salePrice() == null ? BigDecimal.ZERO : req.salePrice());
    }

    @Transactional
    public ItemDto createItem(ItemRequest req, User actor) {
        Item i = new Item();
        applyItem(i, req, null);
        itemRepository.save(i);
        auditService.log("ITEM", i.getId(), i.getCode(), "CREATE", null, i.getName(), actor);
        return toDto(i, true);
    }

    @Transactional
    public ItemDto updateItem(String id, ItemRequest req, User actor) {
        Item i = findItem(id);
        int oldMin = i.getMinStock();
        applyItem(i, req, id);
        auditService.log("ITEM", i.getId(), i.getCode(), "UPDATE", null,
                oldMin != i.getMinStock() ? "Mốc cảnh báo: " + oldMin + " → " + i.getMinStock() : null, actor);
        return toDto(i, true);
    }

    /** UC_08 – Ngừng sử dụng mặt hàng (cảnh báo nếu tồn khác 0). */
    @Transactional
    public ItemDto setItemActive(String id, boolean active, boolean confirm, User actor) {
        Item i = findItem(id);
        if (!active && i.getCurrentStock() != 0 && !confirm) {
            throw ApiException.conflict("NEED_CONFIRM", "Mặt hàng \"" + i.getName() + "\" vẫn còn tồn kho "
                    + i.getCurrentStock() + " " + i.getUnit() + ". Bạn có chắc muốn ngừng sử dụng?");
        }
        if (active && !i.getCategory().isActive()) {
            throw ApiException.badRequest("Loại hàng của mặt hàng này đang ngừng sử dụng");
        }
        i.setActive(active);
        auditService.log("ITEM", i.getId(), i.getCode(), active ? "ACTIVATE" : "DEACTIVATE", null,
                "Tồn kho tại thời điểm: " + i.getCurrentStock(), actor);
        return toDto(i, true);
    }

    /** UC_24 – Thiết lập mốc cảnh báo tồn thấp. */
    @Transactional
    public ItemDto updateThreshold(String id, ThresholdRequest req, User actor) {
        Item i = findItem(id);
        if (req.maxStock() != null && req.maxStock() > 0 && req.maxStock() <= req.minStock()) {
            throw ApiException.badRequest("Mức tồn tối đa phải lớn hơn mốc cảnh báo tồn thấp");
        }
        String detail = "Mốc tồn thấp: " + i.getMinStock() + " → " + req.minStock();
        i.setMinStock(req.minStock());
        i.setMaxStock(req.maxStock() == null || req.maxStock() == 0 ? null : req.maxStock());
        auditService.log("ITEM", i.getId(), i.getCode(), "THRESHOLD", null, detail, actor);
        return toDto(i, true);
    }
}
