package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import vn.songdem.inventory.domain.Enums.MovementType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Thẻ kho – mọi biến động tồn kho đều được ghi lại tại đây. */
@Entity
@Table(name = "the_kho", indexes = {
        @Index(name = "idx_the_kho_item_date", columnList = "item_id,movement_date")
})
@Getter @Setter @NoArgsConstructor
public class StockMovement {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "stock_before", nullable = false)
    private int stockBefore;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** Ngày nghiệp vụ (ngày nhập, ngày bán, ngày kiểm kê). */
    @Column(name = "movement_date", nullable = false)
    private LocalDate movementDate;

    @Column(name = "ref_type", length = 30)
    private String refType;

    @Column(name = "ref_id", length = 36)
    private String refId;

    @Column(name = "ref_code", length = 30)
    private String refCode;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
