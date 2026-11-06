package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.songdem.inventory.domain.Supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, String> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<Supplier> findByCodeIgnoreCase(String code);
    List<Supplier> findAllByOrderByNameAsc();
}
