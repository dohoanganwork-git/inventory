package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/** Lịch sử các lần gửi file kiểm kê của một đợt. */
@Entity
@Table(name = "kiem_ke_lich_su_tep")
@Getter @Setter @NoArgsConstructor
public class StocktakeSubmission {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stocktake_id", nullable = false)
    private Stocktake stocktake;

    /** INITIAL – gửi lần đầu; REPLACE – thay file; RECOUNT – kiểm kê lại theo yêu cầu. */
    @Column(nullable = false, length = 20)
    private String kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "count_file_id")
    private StoredFile countFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "damage_file_id")
    private StoredFile damageFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(name = "variance_lines")
    private int varianceLines;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;
}
