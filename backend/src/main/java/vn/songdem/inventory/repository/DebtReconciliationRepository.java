package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.DebtReconciliation;

import java.time.LocalDate;
import java.util.List;

public interface DebtReconciliationRepository extends JpaRepository<DebtReconciliation, String> {
    long countByCodeStartingWith(String prefix);

    @Query("select d from DebtReconciliation d join fetch d.supplier order by d.createdAt desc")
    List<DebtReconciliation> findAllWithSupplier();

    @Query("select count(d) > 0 from DebtReconciliation d where d.supplier.id = :supplierId and d.status = 'COMPLETED' " +
           "and d.periodFrom <= :date and d.periodTo >= :date")
    boolean isDateLocked(String supplierId, LocalDate date);

    @Query("select d from DebtReconciliation d where d.supplier.id = :supplierId and d.periodFrom <= :to and d.periodTo >= :from")
    List<DebtReconciliation> findOverlapping(String supplierId, LocalDate from, LocalDate to);
}
