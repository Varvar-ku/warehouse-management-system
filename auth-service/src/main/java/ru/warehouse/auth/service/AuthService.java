package ru.warehouse.auth.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.auth.dto.AuthDtos.LoginRequest;
import ru.warehouse.auth.dto.AuthDtos.TokenResponse;
import ru.warehouse.auth.entity.User;
import ru.warehouse.auth.repository.UserRepository;
import ru.warehouse.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /** Хэш для «холостой» проверки, когда логин не найден (выравнивает время ответа). */
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        // BCrypt работает только с первыми 72 байтами пароля; более длинный в любом случае неверный
        if (req.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        String login = req.login().trim().toLowerCase(Locale.ROOT);
        Optional<User> found = userRepository.findByLogin(login);

        // Проверяем пароль ВСЕГДА (даже для несуществующего логина): так по времени ответа
        // нельзя определить, существует ли пользователь. Сообщение об ошибке тоже одинаковое.
        boolean passwordOk = passwordEncoder.matches(req.password(),
                found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            throw invalidCredentials();
        }
        User user = found.get();
        if (!user.isActive()) { // пароль верный, поэтому о блокировке сказать безопасно
            throw ApiException.forbidden("ACCOUNT_DISABLED", "Учётная запись отключена");
        }
        TokenService.IssuedToken token = tokenService.issue(user);
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds(), UserService.toResponse(user));
    }

    private ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Неверный логин или пароль");
    }
}