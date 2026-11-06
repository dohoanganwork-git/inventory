package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Nhà cung cấp (NhaCungCap) – lưu thông tin liên hệ và thông tin hợp đồng. */
@Entity
@Table(name = "nha_cung_cap")
@Getter @Setter @NoArgsConstructor
public class Supplier {
    @Id @GeneratedValue @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(name = "tax_code", length = 20)
    private String taxCode;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "bank_account", length = 150)
    private String bankAccount;

    @Column(name = "contract_no", length = 50)
    private String contractNo;

    @Column(name = "contract_start")
    private LocalDate contractStart;

    @Column(name = "contract_end")
    private LocalDate contractEnd;

    @Column(name = "contract_note", columnDefinition = "TEXT")
    private String contractNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_file_id")
    private StoredFile contractFile;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
