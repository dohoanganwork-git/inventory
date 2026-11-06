package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import vn.songdem.inventory.domain.Enums.LineDecision;

/** Chi tiết kiểm kê (ChiTietKiemKe). */
@Entity
@Table(name = "chi_tiet_kiem_ke")
@Getter @Setter @NoArgsConstructor
public class StocktakeLine {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "line_no")
    private int lineNo;

    /** Tồn sổ sách tại thời điểm đối chiếu. */
    @Column(name = "book_qty", nullable = false)
    private int bookQty;

    /** Số lượng đếm thực tế (hàng còn bán được). */
    @Column(name = "counted_qty", nullable = false)
    private int countedQty;

    @Column(name = "damaged_qty", nullable = false)
    private int damagedQty;

    @Column(name = "damage_reason", length = 255)
    private String damageReason;

    /** Chênh lệch = thực tế − (sổ sách − hỏng/hủy). */
    @Column(name = "variance_qty", nullable = false)
    private int varianceQty;

    /** Chênh lệch ở lần đếm đầu tiên – dùng tính tỉ lệ chính xác kho. */
    @Column(name = "initial_variance_qty", nullable = false)
    private int initialVarianceQty;

    @Column(name = "recount_times", nullable = false)
    private int recountTimes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineDecision decision = LineDecision.NONE;

    /** Lượng tồn thực tế đã điều chỉnh (sau khi Quản lý đồng ý). */
    @Column(name = "adjusted_qty")
    private Integer adjustedQty;

    public boolean needsDecision() {
        return varianceQty != 0 || damagedQty > 0;
    }
}
