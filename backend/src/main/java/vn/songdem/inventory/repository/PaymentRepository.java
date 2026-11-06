package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.Enums.DocStatus;
import vn.songdem.inventory.domain.Payment;

import java.time.LocalDate;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    List<Payment> findByReceiptIdOrderByCreatedAtDesc(String receiptId);
    boolean existsByReceiptIdAndStatus(String receiptId, DocStatus status);
    long countByCodeStartingWith(String prefix);

    @Query("select p from Payment p join fetch p.receipt r join fetch r.supplier where p.status = 'ACTIVE' and p.paidAt between :from and :to order by p.paidAt desc")
    List<Payment> findActiveInPeriod(LocalDate from, LocalDate to);
}
