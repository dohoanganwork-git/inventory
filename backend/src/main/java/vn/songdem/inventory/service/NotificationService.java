package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.songdem.inventory.domain.Enums.NotificationType;
import vn.songdem.inventory.domain.Notification;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.repository.NotificationRepository;
import vn.songdem.inventory.repository.UserRepository;
import vn.songdem.inventory.web.ApiException;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository repository;
    private final UserRepository userRepository;

    public record NotificationDto(String id, NotificationType type, String title, String message, String link,
                                  boolean read, LocalDateTime createdAt) {
        static NotificationDto of(Notification n) {
            return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.isRead(), n.getCreatedAt());
        }
    }

    @Transactional
    public void notifyRoles(Collection<Role> roles, NotificationType type, String title, String message, String link) {
        for (User u : userRepository.findByRoleInAndActiveTrue(roles)) {
            Notification n = new Notification();
            n.setUser(u);
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message != null && message.length() > 1000 ? message.substring(0, 997) + "..." : message);
            n.setLink(link);
            repository.save(n);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(String userId, int limit) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, limit)).stream().map(NotificationDto::of).toList();
    }

    @Transactional(readOnly = true)
    public long unread(String userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(String userId, String id) {
        Notification n = repository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (!n.getUser().getId().equals(userId)) throw ApiException.forbidden("Không thể thao tác thông báo của người khác");
        n.setRead(true);
    }

    @Transactional
    public void markAllRead(String userId) {
        repository.markAllRead(userId);
    }
}
