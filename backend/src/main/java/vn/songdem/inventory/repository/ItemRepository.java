package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.Item;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, String> {
    Optional<Item> findByCodeIgnoreCase(String code);
    Optional<Item> findByPosCodeIgnoreCase(String posCode);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByPosCodeIgnoreCase(String posCode);
    long countByActiveTrue();
    long countByCategoryId(String categoryId);
    long countBySupplierId(String supplierId);

    @Query("select i from Item i left join fetch i.category left join fetch i.supplier order by i.name asc")
    List<Item> findAllWithRefs();

    @Query("select i from Item i left join fetch i.category left join fetch i.supplier where i.active = true and i.currentStock <= i.minStock order by i.currentStock asc")
    List<Item> findLowStock();
}
