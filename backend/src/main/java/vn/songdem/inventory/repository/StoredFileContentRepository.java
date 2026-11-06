package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.songdem.inventory.domain.StoredFileContent;

public interface StoredFileContentRepository extends JpaRepository<StoredFileContent, String> {
}
