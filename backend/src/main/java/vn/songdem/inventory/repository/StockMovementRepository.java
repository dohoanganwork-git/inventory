package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.StockMovement;

import java.time.LocalDate;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, String> {
    @Query("select m from StockMovement m left join fetch m.createdBy where m.item.id = :itemId order by m.createdAt desc")
    List<StockMovement> findByItem(String itemId);

    @Query("select m from StockMovement m where m.movementDate >= :from")
    List<StockMovement> findFromDate(LocalDate from);
}
