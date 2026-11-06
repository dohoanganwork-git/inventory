package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import vn.songdem.inventory.domain.Enums.DebtReconResult;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Một dòng kết quả đối chiếu công nợ. */
@Entity
@Table(name = "chi_tiet_doi_soat_cong_no")
@Getter @Setter @NoArgsConstructor
public class DebtReconciliationLine {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reconciliation_id", nullable = false)
    private DebtReconciliation reconciliation;

    @Column(name = "line_no")
    private int lineNo;

    @Column(name = "invoice_no", length = 50)
    private String invoiceNo;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id")
    private GoodsReceipt receipt;

    @Column(name = "file_amount", precision = 15, scale = 2)
    private BigDecimal fileAmount;

    @Column(name = "file_paid", precision = 15, scale = 2)
    private BigDecimal filePaid;

    @Column(name = "system_amount", precision = 15, scale = 2)
    private BigDecimal systemAmount;

    @Column(name = "system_paid", precision = 15, scale = 2)
    private BigDecimal systemPaid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DebtReconResult result;

    @Column(length = 500)
    private String note;
}
