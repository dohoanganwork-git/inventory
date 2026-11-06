package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/** Thông tin tệp đã tải lên (nội dung lưu riêng ở bảng tep_tin_noi_dung). */
@Entity
@Table(name = "tep_tin")
@Getter @Setter @NoArgsConstructor
public class StoredFile {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes")
    private long size;

    /** Phân loại: POS, STOCKTAKE, DAMAGE, INVOICE, EVIDENCE, CONTRACT, DEBT_INVOICE... */
    @Column(length = 30)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}
