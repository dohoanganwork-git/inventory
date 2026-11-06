package vn.songdem.inventory.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.songdem.inventory.dto.AuthDtos.*;
import vn.songdem.inventory.dto.CommonDtos.StatusRequest;
import vn.songdem.inventory.security.CurrentUser;
import vn.songdem.inventory.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class UserController {
    private final UserService userService;
    private final CurrentUser currentUser;

    @GetMapping
    public List<UserDto> list() {
        return userService.list();
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody UserCreateRequest req) {
        return userService.create(req, currentUser.get());
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable String id, @Valid @RequestBody UserUpdateRequest req) {
        return userService.update(id, req, currentUser.get());
    }

    @PatchMapping("/{id}/status")
    public UserDto status(@PathVariable String id, @RequestBody StatusRequest req) {
        return userService.setActive(id, Boolean.TRUE.equals(req.active()), currentUser.get());
    }
}
