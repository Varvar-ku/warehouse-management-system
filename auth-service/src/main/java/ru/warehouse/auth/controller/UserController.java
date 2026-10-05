package ru.warehouse.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.auth.dto.AuthDtos.*;
import ru.warehouse.auth.service.UserService;
import ru.warehouse.common.dto.PageResponse;
import java.util.List;
import java.util.UUID;

/** Всё в этом контроллере доступно только администратору. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {
    private final UserService userService;

    @GetMapping("/users")
    public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return userService.list(page, size);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return userService.create(request, actor(jwt));
    }

    @GetMapping("/users/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return userService.get(id);
    }

    @PatchMapping("/users/{id}")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        return userService.update(id, request, actor(jwt));
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        userService.resetPassword(id, request.newPassword(), actor(jwt));
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles() {
        return userService.listRoles();
    }

    private static UUID actor(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}