package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vn.songdem.inventory.domain.AuditLog;

import java.util.Collection;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    @Query("select a from AuditLog a left join fetch a.user where a.entityId in :ids order by a.createdAt desc")
    List<AuditLog> findByEntityIds(Collection<String> ids);
}
