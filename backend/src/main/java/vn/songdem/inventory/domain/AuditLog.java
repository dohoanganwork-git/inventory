package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/** Lịch sử thay đổi chứng từ (người sửa, thời gian, lý do). */
@Entity
@Table(name = "lich_su_thay_doi", indexes = {
        @Index(name = "idx_lstd_entity", columnList = "entity_type,entity_id")
})
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 36)
    private String entityId;

    @Column(name = "entity_code", length = 30)
    private String entityCode;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 1000)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
