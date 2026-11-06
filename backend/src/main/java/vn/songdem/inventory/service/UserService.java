package vn.songdem.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.User;
import vn.songdem.inventory.dto.AuthDtos.*;
import vn.songdem.inventory.repository.UserRepository;
import vn.songdem.inventory.security.JwtService;
import vn.songdem.inventory.util.TextUtil;
import vn.songdem.inventory.web.ApiException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public static void validatePassword(String pw) {
        if (pw == null || pw.length() < 8) throw ApiException.badRequest("Mật khẩu phải có tối thiểu 8 ký tự");
    }

    /** UC_01 – Đăng nhập. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User u = userRepository.findByUsernameIgnoreCase(req.username().trim())
                .orElseThrow(() -> ApiException.unauthorized("BAD_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.unauthorized("BAD_CREDENTIALS", "Tên đăng nhập hoặc mật khẩu không đúng");
        }
        if (!u.isActive()) {
            throw ApiException.forbidden("Tài khoản đã bị vô hiệu hóa. Vui lòng liên hệ Quản lý để được hỗ trợ.");
        }
        return new LoginResponse(jwtService.generate(u), UserDto.of(u));
    }

    /** UC_03 – Đổi mật khẩu. */
    @Transactional
    public void changePassword(User user, ChangePasswordRequest req) {
        User u = userRepository.findById(user.getId()).orElseThrow();
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không đúng");
        }
        validatePassword(req.newPassword());
        if (req.newPassword().equals(req.currentPassword())) {
            throw ApiException.badRequest("Mật khẩu mới không được trùng mật khẩu cũ");
        }
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw ApiException.badRequest("Xác nhận mật khẩu không khớp");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    /** UC_04 – Cập nhật thông tin cá nhân. */
    @Transactional
    public UserDto updateProfile(User user, ProfileRequest req) {
        User u = userRepository.findById(user.getId()).orElseThrow();
        u.setFullName(req.fullName().trim());
        u.setEmail(TextUtil.trimToNull(req.email()));
        u.setPhone(TextUtil.trimToNull(req.phone()));
        return UserDto.of(u);
    }

    @Transactional(readOnly = true)
    public List<UserDto> list() {
        return userRepository.findAllByOrderByCreatedAtAsc().stream().map(UserDto::of).toList();
    }

    /** UC_05 – Tạo mới tài khoản. */
    @Transactional
    public UserDto create(UserCreateRequest req, User actor) {
        if (userRepository.existsByUsernameIgnoreCase(req.username().trim())) {
            throw ApiException.conflict("DUPLICATE_USERNAME", "Tên đăng nhập \"" + req.username() + "\" đã tồn tại");
        }
        validatePassword(req.password());
        User u = new User();
        u.setUsername(req.username().trim());
        u.setFullName(req.fullName().trim());
        u.setEmail(TextUtil.trimToNull(req.email()));
        u.setPhone(TextUtil.trimToNull(req.phone()));
        u.setRole(req.role());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setActive(true);
        userRepository.save(u);
        auditService.log("USER", u.getId(), u.getUsername(), "CREATE", null, "Vai trò: " + u.getRole().getLabel(), actor);
        return UserDto.of(u);
    }

    /** UC_06 – Chỉnh sửa tài khoản. */
    @Transactional
    public UserDto update(String id, UserUpdateRequest req, User actor) {
        User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (u.getRole() == Role.MANAGER && req.role() != Role.MANAGER) {
            if (u.getId().equals(actor.getId())) {
                throw ApiException.badRequest("Bạn không thể tự hạ vai trò của chính mình");
            }
            if (u.isActive() && userRepository.countByRoleAndActiveTrue(Role.MANAGER) <= 1) {
                throw ApiException.badRequest("Không thể đổi vai trò của tài khoản Quản lý cuối cùng");
            }
        }
        String oldRole = u.getRole().getLabel();
        u.setFullName(req.fullName().trim());
        u.setEmail(TextUtil.trimToNull(req.email()));
        u.setPhone(TextUtil.trimToNull(req.phone()));
        u.setRole(req.role());
        if (!TextUtil.isBlank(req.newPassword())) {
            validatePassword(req.newPassword());
            u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        }
        auditService.log("USER", u.getId(), u.getUsername(), "UPDATE", null,
                oldRole.equals(u.getRole().getLabel()) ? "Cập nhật thông tin" : "Đổi vai trò: " + oldRole + " → " + u.getRole().getLabel(), actor);
        return UserDto.of(u);
    }

    /** UC_06 – Vô hiệu hóa / kích hoạt lại tài khoản (không xóa để giữ lịch sử). */
    @Transactional
    public UserDto setActive(String id, boolean active, User actor) {
        User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản"));
        if (!active) {
            if (u.getId().equals(actor.getId())) throw ApiException.badRequest("Bạn không thể tự vô hiệu hóa tài khoản của mình");
            if (u.getRole() == Role.MANAGER && u.isActive() && userRepository.countByRoleAndActiveTrue(Role.MANAGER) <= 1) {
                throw ApiException.badRequest("Không thể vô hiệu hóa tài khoản Quản lý cuối cùng");
            }
        }
        u.setActive(active);
        auditService.log("USER", u.getId(), u.getUsername(), active ? "ACTIVATE" : "DEACTIVATE", null, null, actor);
        return UserDto.of(u);
    }
}
