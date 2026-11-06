package vn.songdem.inventory.dto;

import jakarta.validation.constraints.*;
import vn.songdem.inventory.dto.CommonDtos.FileDto;
import vn.songdem.inventory.dto.CommonDtos.RefDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryRequest(
            @NotBlank(message = "Vui lòng nhập mã loại hàng") @Size(max = 30, message = "Mã tối đa 30 ký tự") String code,
            @NotBlank(message = "Vui lòng nhập tên loại hàng") @Size(max = 100, message = "Tên tối đa 100 ký tự") String name,
            @Size(max = 500, message = "Mô tả tối đa 500 ký tự") String description) {}

    public record CategoryDto(String id, String code, String name, String description, boolean active,
                              long itemCount, LocalDateTime createdAt) {}

    public record SupplierRequest(
            @NotBlank(message = "Vui lòng nhập mã nhà cung cấp") @Size(max = 30, message = "Mã tối đa 30 ký tự") String code,
            @NotBlank(message = "Vui lòng nhập tên nhà cung cấp") @Size(max = 150) String name,
            @Size(max = 100) String contactName,
            @Pattern(regexp = "^$|^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng") String phone,
            @Email(message = "Email không đúng định dạng") String email,
            @Size(max = 255) String address,
            @Size(max = 20) String taxCode,
            @Size(max = 100) String paymentMethod,
            @Size(max = 150) String bankAccount,
            @Size(max = 50) String contractNo,
            LocalDate contractStart,
            LocalDate contractEnd,
            String contractNote) {}

    public record SupplierDto(String id, String code, String name, String contactName, String phone, String email,
                              String address, String taxCode, String paymentMethod, String bankAccount,
                              String contractNo, LocalDate contractStart, LocalDate contractEnd, String contractNote,
                              String contractStatus, FileDto contractFile, boolean active, long itemCount,
                              BigDecimal openDebt, LocalDateTime createdAt) {}

    public record ItemRequest(
            @NotBlank(message = "Vui lòng nhập mã hàng") @Size(max = 30, message = "Mã hàng tối đa 30 ký tự") String code,
            @NotBlank(message = "Vui lòng nhập mã trên POS") @Size(max = 50, message = "Mã POS tối đa 50 ký tự") String posCode,
            @NotBlank(message = "Vui lòng nhập tên mặt hàng") @Size(max = 150) String name,
            @NotBlank(message = "Vui lòng nhập đơn vị tính") @Size(max = 20) String unit,
            @NotBlank(message = "Vui lòng chọn loại hàng") String categoryId,
            String supplierId,
            @NotNull(message = "Vui lòng nhập mốc cảnh báo tồn thấp") @Min(value = 0, message = "Mốc cảnh báo không được nhỏ hơn 0") Integer minStock,
            @Min(value = 0, message = "Mức tồn tối đa không được nhỏ hơn 0") Integer maxStock,
            @DecimalMin(value = "0", message = "Giá bán không được âm") BigDecimal salePrice) {}

    public record ItemDto(String id, String code, String posCode, String name, String unit,
                          RefDto category, RefDto supplier, int minStock, Integer maxStock, int currentStock,
                          BigDecimal lastCost, BigDecimal salePrice, BigDecimal stockValue, String stockStatus,
                          boolean active, LocalDateTime createdAt) {}

    public record ThresholdRequest(
            @NotNull(message = "Vui lòng nhập mốc cảnh báo") @Min(value = 0, message = "Mốc cảnh báo không được nhỏ hơn 0") Integer minStock,
            @Min(value = 0, message = "Mức tồn tối đa không được nhỏ hơn 0") Integer maxStock) {}

    public record MovementDto(String id, String type, int quantityChange, int stockBefore, int stockAfter,
                              LocalDate movementDate, String refType, String refId, String refCode, String note,
                              String createdBy, LocalDateTime createdAt) {}
}
