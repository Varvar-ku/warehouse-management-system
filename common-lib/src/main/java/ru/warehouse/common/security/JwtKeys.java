package ru.warehouse.common.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Строит HMAC-ключ из секрета. Для HS256 нужно не менее 256 бит (32 байта). */
public final class JwtKeys {
    /** Публичные dev-секреты из репозитория: вне профилей dev и test недопустимы, даже если переданы через JWT_SECRET. */
    private static final Set<String> DEV_SECRETS = Set.of("dev-only-secret-change-me-0123456789abcdef");
    private static final Profiles DEV_PROFILES = Profiles.of("dev", "test"); // профиль default сюда НЕ входит

    private JwtKeys() {}

    public static SecretKey secretKey(JwtProperties props, Environment env) {
        byte[] bytes = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret должен быть не короче 32 байт. Задайте переменную окружения JWT_SECRET "
                            + "(для локальной разработки включите профиль dev).");
        }
        if (DEV_SECRETS.contains(props.secret()) && !env.acceptsProfiles(DEV_PROFILES)) {
            throw new IllegalStateException(
                    "Используется публичный dev-секрет вне профилей dev/test. Задайте собственный JWT_SECRET.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}