package vn.songdem.inventory.dto;

import vn.songdem.inventory.domain.*;

import java.time.LocalDateTime;

public final class CommonDtos {
    private CommonDtos() {}

    public record RefDto(String id, String code, String name) {
        public static RefDto of(Category c) { return c == null ? null : new RefDto(c.getId(), c.getCode(), c.getName()); }
        public static RefDto of(Supplier s) { return s == null ? null : new RefDto(s.getId(), s.getCode(), s.getName()); }
        public static RefDto of(Item i) { return i == null ? null : new RefDto(i.getId(), i.getCode(), i.getName()); }
    }

    public record UserRef(String id, String fullName, String role) {
        public static UserRef of(User u) {
            return u == null ? null : new UserRef(u.getId(), u.getFullName(), u.getRole().getLabel());
        }
    }

    public record FileDto(String id, String name, String contentType, long size, LocalDateTime uploadedAt) {
        public static FileDto of(StoredFile f) {
            return f == null ? null : new FileDto(f.getId(), f.getOriginalName(), f.getContentType(), f.getSize(), f.getUploadedAt());
        }
    }

    public record AuditDto(String id, String action, String reason, String detail, UserRef user, LocalDateTime createdAt) {
        public static AuditDto of(AuditLog a) {
            return new AuditDto(a.getId(), a.getAction(), a.getReason(), a.getDetail(), UserRef.of(a.getUser()), a.getCreatedAt());
        }
    }

    public record ReasonRequest(String reason) {}

    public record StatusRequest(Boolean active, Boolean confirm) {}

    public record MessageResponse(String message) {}
}
