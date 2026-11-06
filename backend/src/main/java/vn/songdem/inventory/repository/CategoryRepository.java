package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.songdem.inventory.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {
    boolean existsByCodeIgnoreCase(String code);
    Optional<Category> findByCodeIgnoreCase(String code);
    List<Category> findAllByOrderByNameAsc();
}
