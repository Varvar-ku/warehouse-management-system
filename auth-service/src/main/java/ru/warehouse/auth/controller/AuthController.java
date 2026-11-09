package ru.warehouse.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import ru.warehouse.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.auth.dto.AuthDtos.*;
import ru.warehouse.auth.service.AuthService;
import ru.warehouse.auth.service.UserService;
import ru.warehouse.auth.throttle.ClientIp;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserService userService;

    /** false: пароли задаёт только администратор (POST /users/{id}/reset-password), смена пароля самим пользователем закрыта. */
    /** Число доверенных прокси между клиентом и auth-service: 1 = gateway, 2 = nginx и gateway (см. ClientIp). */
    @Value("${app.security.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    @Value("${app.security.self-service-password-change:false}")
    private boolean selfServicePasswordChange;

    /** Публичный эндпоинт (разрешён в app.security.public-paths). Не логируйте тело запроса: там пароль. */
    @PostMapping("/auth/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, ClientIp.resolve(http, trustedProxyHops));
    }
    /** Текущий пользователь и его роли. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.me(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        if (!selfServicePasswordChange) {
            throw ApiException.forbidden("PASSWORD_CHANGE_DISABLED", "Пароль задаёт администратор: обратитесь к нему");
        }
        userService.changeOwnPassword(UUID.fromString(jwt.getSubject()), request);
    }
}