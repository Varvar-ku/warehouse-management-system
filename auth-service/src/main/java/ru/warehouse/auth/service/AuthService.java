package ru.warehouse.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.auth.dto.AuthDtos.LoginRequest;
import ru.warehouse.auth.dto.AuthDtos.TokenResponse;
import ru.warehouse.auth.entity.User;
import ru.warehouse.auth.repository.UserRepository;
import ru.warehouse.auth.throttle.LoginThrottleService;
import ru.warehouse.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginThrottleService throttle;
    private final AuthAudit audit;

    /** Хэш для «холостой» проверки, когда логин не найден (выравнивает время ответа). */
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * noRollbackFor: счётчик неудачных попыток должен сохраниться, даже когда метод
     * завершается ошибкой 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public TokenResponse login(LoginRequest req, String clientIp) {
        String login = req.login().trim().toLowerCase(Locale.ROOT);
        LoginThrottleService.Keys keys = throttle.keys(login, clientIp);
        log.debug("Попытка входа с адреса {}", clientIp); // логин и пароль не логируем

        // Блокировка действует одинаково для существующих и несуществующих логинов
        long locked = throttle.remainingLockSeconds(keys);
        if (locked > 0) {
            throw ApiException.tooManyRequests("LOGIN_THROTTLED",
                    "Слишком много неудачных попыток входа. Повторите через " + locked + " с.", locked);
        }

        // BCrypt работает только с первыми 72 байтами пароля; более длинный в любом случае неверный
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throttle.recordFailure(keys);
            auditFailure(null, clientIp, "BAD_PASSWORD");
            throw invalidCredentials();
        }
        Optional<User> found = userRepository.findByLogin(login);

        // Проверяем пароль ВСЕГДА (даже для несуществующего логина): так по времени ответа
        // нельзя определить, существует ли пользователь. Сообщение об ошибке тоже одинаковое.
        boolean passwordOk = passwordEncoder.matches(req.password(),
                found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            throttle.recordFailure(keys);
            auditFailure(found.map(User::getId).orElse(null), clientIp, found.isEmpty() ? "UNKNOWN_LOGIN" : "BAD_PASSWORD");
            throw invalidCredentials();
        }
        User user = found.get();
        if (!user.isActive()) { // пароль верный, поэтому о блокировке сказать безопасно
            auditFailure(user.getId(), clientIp, "ACCOUNT_DISABLED");
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Учётная запись отключена");
        }
        throttle.recordSuccess(keys);
        audit.record("auth.login_succeeded", "USER", user.getId(), user.getId(), "clientIp", clientIp);
        TokenService.IssuedToken token = tokenService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), UserService.toResponse(user));
    }

    /**
     * S4: неудачный вход. Сам логин не записываем (в это поле иногда вводят пароль): для существующей учётной записи
     * пишется её идентификатор, для неизвестного логина только адрес клиента. Заблокированные попытки не пишутся
     * (иначе перебор заполнял бы журнал). Запись сохраняется, несмотря на 401: noRollbackFor.
     */
    private void auditFailure(UUID userId, String clientIp, String reason) {
        audit.record("auth.login_failed", userId != null ? "USER" : "LOGIN_ATTEMPT",
                userId != null ? userId : clientIp, null, "clientIp", clientIp, "reason", reason);
    }

    private ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Неверный логин или пароль");
    }
}