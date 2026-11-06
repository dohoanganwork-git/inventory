package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.Enums.DocStatus;
import vn.songdem.inventory.domain.SalesExport;

import java.time.LocalDate;
import java.util.Optional;

public interface SalesExportRepository extends JpaRepository<SalesExport, String>, JpaSpecificationExecutor<SalesExport> {
    boolean existsBySalesDateAndStatus(LocalDate salesDate, DocStatus status);
    long countByCodeStartingWith(String prefix);

    @Query("select max(s.salesDate) from SalesExport s where s.status = 'ACTIVE'")
    Optional<LocalDate> findLatestSalesDate();
}
