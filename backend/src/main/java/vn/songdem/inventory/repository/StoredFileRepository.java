package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.songdem.inventory.domain.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, String> {
}
