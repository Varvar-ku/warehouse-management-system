package ru.warehouse.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import ru.warehouse.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.warehouse.auth.dto.AuthDtos.*;
import ru.warehouse.auth.service.AuthService;
import ru.warehouse.auth.service.RefreshTokenService;
import ru.warehouse.auth.service.UserService;
import ru.warehouse.auth.throttle.ClientIp;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {
    /** Refresh-токен живёт только в HttpOnly-cookie: скрипты страницы его не видят. */
    private static final String REFRESH_COOKIE = "wms_refresh";
    /** Cookie уходит только на эндпоинты auth (login, refresh, logout), а не на каждый запрос API. */
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final UserService userService;

    /** false: пароли задаёт только администратор (POST /users/{id}/reset-password), смена пароля самим пользователем закрыта. */
    @Value("${app.security.self-service-password-change:false}")
    private boolean selfServicePasswordChange;

    /** Флаг Secure у cookie с refresh-токеном. Отключается только для доступа по HTTP не с localhost. */
    @Value("${app.security.refresh.cookie-secure:true}")
    private boolean refreshCookieSecure;

    /** Публичный эндпоинт (разрешён в app.security.public-paths). Не логируйте тело запроса: там пароль. */
    @PostMapping("/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return respond(authService.login(request, ClientIp.resolve(http)));
    }

    /**
     * P1-8: новый access-токен по refresh-токену из cookie. Публичный путь: access-токен к этому моменту уже
     * просрочен. При отказе cookie удаляется, а клиент должен перейти на экран входа.
     */
    @PostMapping("/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse http) {
        try {
            return respond(authService.refresh(refreshToken));
        } catch (ApiException e) {
            http.addHeader(HttpHeaders.SET_COOKIE, clearedCookie().toString());
            throw e;
        }
    }

    /** P1-8: выход. Отзывает сессию на сервере и удаляет cookie. Идемпотентен, всегда 204. */
    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                       HttpServletResponse http) {
        authService.logout(refreshToken);
        http.addHeader(HttpHeaders.SET_COOKIE, clearedCookie().toString());
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

    private ResponseEntity<TokenResponse> respond(AuthService.LoginSession session) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        RefreshTokenService.Issued refresh = session.refresh();
        if (refresh != null) { // null: параллельное обновление, браузер уже получил новый токен
            Duration maxAge = Duration.between(OffsetDateTime.now(), refresh.expiresAt());
            builder.header(HttpHeaders.SET_COOKIE, refreshCookie(refresh.value(), maxAge).toString());
        }
        return builder.body(session.response());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        // SameSite=Strict: cookie не уходит с запросов, инициированных другими сайтами
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    private ResponseCookie clearedCookie() {
        return refreshCookie("", Duration.ZERO);
    }
}
