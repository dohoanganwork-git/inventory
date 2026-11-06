package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.Enums.StocktakeStatus;
import vn.songdem.inventory.domain.Stocktake;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StocktakeRepository extends JpaRepository<Stocktake, String> {
    long countByCodeStartingWith(String prefix);
    List<Stocktake> findAllByOrderByStocktakeDateDescCreatedAtDesc();
    List<Stocktake> findByStatusIn(Collection<StocktakeStatus> statuses);
    boolean existsByStocktakeDateAndStatus(LocalDate date, StocktakeStatus status);
    long countByStatus(StocktakeStatus status);

    @Query("select max(s.stocktakeDate) from Stocktake s where s.status = 'COMPLETED'")
    Optional<LocalDate> findLatestCompletedDate();

    @Query("select s from Stocktake s where s.stocktakeDate between :from and :to order by s.stocktakeDate desc")
    List<Stocktake> findInPeriod(LocalDate from, LocalDate to);
}
