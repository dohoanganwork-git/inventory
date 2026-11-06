package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.SalesExportLine;

import java.time.LocalDate;
import java.util.List;

public interface SalesExportLineRepository extends JpaRepository<SalesExportLine, String> {
    @Query("select l from SalesExportLine l join fetch l.salesExport s join fetch l.item i left join fetch i.category " +
           "where s.status = 'ACTIVE' and s.salesDate between :from and :to")
    List<SalesExportLine> findActiveInPeriod(LocalDate from, LocalDate to);

    @Query("select l from SalesExportLine l join fetch l.salesExport s join fetch l.item " +
           "where s.status = 'ACTIVE' and l.overQuantity > 0 and s.salesDate >= :from order by s.salesDate desc")
    List<SalesExportLine> findOverSoldSince(LocalDate from);
}
