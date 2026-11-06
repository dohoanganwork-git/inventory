package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.Enums.DocStatus;
import vn.songdem.inventory.domain.GoodsReceipt;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, String>, JpaSpecificationExecutor<GoodsReceipt> {
    boolean existsBySupplierIdAndInvoiceNoIgnoreCaseAndStatus(String supplierId, String invoiceNo, DocStatus status);
    boolean existsBySupplierIdAndInvoiceNoIgnoreCaseAndStatusAndIdNot(String supplierId, String invoiceNo, DocStatus status, String id);
    Optional<GoodsReceipt> findFirstBySupplierIdAndInvoiceNoIgnoreCaseAndStatus(String supplierId, String invoiceNo, DocStatus status);
    long countByCodeStartingWith(String prefix);

    @Query("select r from GoodsReceipt r where r.supplier.id = :supplierId and r.status = 'ACTIVE' and r.receiptDate between :from and :to order by r.receiptDate")
    List<GoodsReceipt> findActiveBySupplierInPeriod(String supplierId, LocalDate from, LocalDate to);

    @Query("select r from GoodsReceipt r join fetch r.supplier where r.status = 'ACTIVE' and r.remainingAmount > 0 order by r.receiptDate")
    List<GoodsReceipt> findOpenDebts();

    @Query("select r from GoodsReceipt r join fetch r.supplier where r.status = 'ACTIVE'")
    List<GoodsReceipt> findAllActiveWithSupplier();
}
