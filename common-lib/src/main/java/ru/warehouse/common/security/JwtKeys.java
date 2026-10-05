package ru.warehouse.common.security;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Строит HMAC-ключ из секрета. Для HS256 нужно не менее 256 бит (32 байта). */
public final class JwtKeys {
    private JwtKeys() {}

    public static SecretKey secretKey(JwtProperties props) {
        byte[] bytes = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret должен быть не короче 32 байт. Задайте переменную окружения JWT_SECRET.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}