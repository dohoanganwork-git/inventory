package vn.songdem.inventory.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.songdem.inventory.dto.AuthDtos.*;
import vn.songdem.inventory.dto.CommonDtos.MessageResponse;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.UserService;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final CurrentUser currentUser;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req);
    }

    @GetMapping("/auth/me")
    public UserDto me() {
        return UserDto.of(currentUser.get());
    }

    @PutMapping("/auth/me")
    public UserDto updateProfile(@Valid @RequestBody ProfileRequest req) {
        return userService.updateProfile(currentUser.get(), req);
    }

    @PostMapping("/auth/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(currentUser.get(), req);
        return new MessageResponse("Đổi mật khẩu thành công");
    }

    /** Đăng xuất phía máy chủ không cần lưu trạng thái (JWT); giao diện xóa token. */
    @PostMapping("/auth/logout")
    public MessageResponse logout() {
        return new MessageResponse("Đã đăng xuất");
    }
}
