package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;

/** Chi tiết phiếu xuất bán. */
@Entity
@Table(name = "chi_tiet_phieu_xuat")
@Getter @Setter @NoArgsConstructor
public class SalesExportLine {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_export_id", nullable = false)
    private SalesExport salesExport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "line_no")
    private int lineNo;

    /** Số lượng bán theo file POS. */
    @Column(nullable = false)
    private int quantity;

    /** Số lượng thực trừ khỏi tồn kho (không vượt quá tồn). */
    @Column(name = "deducted_quantity", nullable = false)
    private int deductedQuantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "stock_before", nullable = false)
    private int stockBefore;

    @Column(name = "stock_after", nullable = false)
    private int stockAfter;

    /** Phần bán vượt tồn kho. */
    @Column(name = "over_quantity", nullable = false)
    private int overQuantity;
}
