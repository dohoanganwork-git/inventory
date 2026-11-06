package vn.songdem.inventory.security;

import vn.songdem.inventory.domain.Role;

/** Thông tin người dùng đang đăng nhập. */
public record AppUserPrincipal(String id, String username, String fullName, Role role) {
}
