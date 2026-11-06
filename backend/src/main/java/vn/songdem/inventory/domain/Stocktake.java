package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;
import vn.songdem.inventory.domain.Enums.StocktakeStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Đợt kiểm kê và đối soát tồn kho (KiemKeVaDoiSoat). */
@Entity
@Table(name = "kiem_ke_va_doi_soat")
@Getter @Setter @NoArgsConstructor
public class Stocktake {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(name = "stocktake_date", nullable = false)
    private LocalDate stocktakeDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "count_file_id")
    private StoredFile countFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "damage_file_id")
    private StoredFile damageFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StocktakeStatus status = StocktakeStatus.PENDING_REVIEW;

    /** Số lần kiểm kê (1 = lần đầu, tăng dần mỗi lần kiểm kê lại). */
    @Column(nullable = false)
    private int round = 1;

    @Column(name = "decision_reason", length = 1000)
    private String decisionReason;

    @Column(name = "recount_reason", length = 1000)
    private String recountReason;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "total_lines")
    private int totalLines;

    @Column(name = "matched_lines")
    private int matchedLines;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<StocktakeLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("submittedAt DESC")
    private List<StocktakeSubmission> submissions = new ArrayList<>();

    @Version
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
