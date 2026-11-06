package vn.songdem.inventory.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nội dung nhị phân của tệp (tách bảng để không tải dữ liệu lớn khi chỉ cần thông tin tệp). */
@Entity
@Table(name = "tep_tin_noi_dung")
@Getter @Setter @NoArgsConstructor
public class StoredFileContent {
    @Id
    @Column(name = "file_id", length = 36)
    private String fileId;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] data;
}
