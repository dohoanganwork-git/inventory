package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.songdem.inventory.domain.AuditLog;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.dto.CommonDtos.AuditDto;
import vn.songdem.inventory.repository.AuditLogRepository;
import vn.songdem.inventory.repository.UserRepository;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    public void log(String entityType, String entityId, String entityCode, String action, String reason, String detail, User user) {
        AuditLog a = new AuditLog();
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setEntityCode(entityCode);
        a.setAction(action);
        a.setReason(reason);
        a.setDetail(detail);
        // Dùng tham chiếu được quản lý trong phiên hiện tại (actor có thể là đối tượng detached)
        a.setUser(user == null ? null : userRepository.getReferenceById(user.getId()));
        repository.save(a);
    }

    public List<AuditDto> history(Collection<String> entityIds) {
        if (entityIds.isEmpty()) return List.of();
        return repository.findByEntityIds(entityIds).stream().map(AuditDto::of).toList();
    }
}
