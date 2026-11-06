package vn.songdem.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.User;

import java.time.LocalDateTime;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập") String username,
            @NotBlank(message = "Vui lòng nhập mật khẩu") String password) {}

    public record LoginResponse(String token, UserDto user) {}

    public record ChangePasswordRequest(
            @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại") String currentPassword,
            @NotBlank(message = "Vui lòng nhập mật khẩu mới") String newPassword,
            @NotBlank(message = "Vui lòng xác nhận mật khẩu mới") String confirmPassword) {}

    public record ProfileRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") @Size(max = 100, message = "Họ tên tối đa 100 ký tự") String fullName,
            @Email(message = "Email không đúng định dạng") String email,
            @Pattern(regexp = "^$|^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng") String phone) {}

    public record UserCreateRequest(
            @NotBlank(message = "Vui lòng nhập tên đăng nhập")
            @Pattern(regexp = "^[a-zA-Z0-9._-]{3,50}$", message = "Tên đăng nhập 3–50 ký tự, chỉ gồm chữ không dấu, số, dấu chấm, gạch") String username,
            @NotBlank(message = "Vui lòng nhập họ tên") String fullName,
            @Email(message = "Email không đúng định dạng") String email,
            @Pattern(regexp = "^$|^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng") String phone,
            @NotNull(message = "Vui lòng chọn vai trò") Role role,
            @NotBlank(message = "Vui lòng nhập mật khẩu ban đầu") String password) {}

    public record UserUpdateRequest(
            @NotBlank(message = "Vui lòng nhập họ tên") String fullName,
            @Email(message = "Email không đúng định dạng") String email,
            @Pattern(regexp = "^$|^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không đúng định dạng") String phone,
            @NotNull(message = "Vui lòng chọn vai trò") Role role,
            String newPassword) {}

    public record UserDto(String id, String username, String fullName, String email, String phone,
                          Role role, String roleLabel, boolean active, LocalDateTime createdAt) {
        public static UserDto of(User u) {
            return new UserDto(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getRole(), u.getRole().getLabel(), u.isActive(), u.getCreatedAt());
        }
    }
}
