package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mặt hàng (MatHang). */
@Entity
@Table(name = "mat_hang")
@Getter @Setter @NoArgsConstructor
public class Item {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "pos_code", nullable = false, unique = true, length = 50)
    private String posCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    /** Mốc cảnh báo tồn thấp. */
    @Column(name = "min_stock", nullable = false)
    private int minStock;

    /** Mức tồn tối đa (tùy chọn) – dùng cảnh báo tồn vượt mức. */
    @Column(name = "max_stock")
    private Integer maxStock;

    @Column(name = "current_stock", nullable = false)
    private int currentStock;

    @Column(name = "last_cost", precision = 15, scale = 2)
    private BigDecimal lastCost = BigDecimal.ZERO;

    @Column(name = "sale_price", precision = 15, scale = 2)
    private BigDecimal salePrice = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public String stockStatus() {
        if (currentStock <= minStock) return "LOW";
        if (maxStock != null && maxStock > 0 && currentStock > maxStock) return "OVER";
        return "NORMAL";
    }
}
