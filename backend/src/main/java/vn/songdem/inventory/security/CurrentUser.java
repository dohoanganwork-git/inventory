package vn.songdem.inventory.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.repository.UserRepository;
import vn.songdem.inventory.web.ApiException;

/** Truy xuất người dùng đang đăng nhập. */
@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository userRepository;

    public AppUserPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal p)) {
            throw ApiException.unauthorized("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại");
        }
        return p;
    }

    public User get() {
        return userRepository.findById(principal().id())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Không tìm thấy tài khoản"));
    }

    public Role role() {
        return principal().role();
    }

    /** Nhân viên không được xem số liệu giá trị và công nợ. */
    public boolean canSeeValues() {
        return role() != Role.STAFF;
    }
}
