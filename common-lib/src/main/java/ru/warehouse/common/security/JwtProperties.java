package ru.warehouse.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

/**
 * Настройки JWT: app.security.jwt.* (P2-11: RS256 вместо общего секрета HS256).
 * <ul>
 *   <li>issuer, audience: проверяются во всех сервисах; audience добавляется в токен при выдаче;</li>
 *   <li>ttl: срок access-токена, используется только auth-service;</li>
 *   <li>jwksUri: адрес публичных ключей auth-service (все сервисы кроме auth); publicKeyFile: файл публичного
 *       ключа вместо jwksUri (тесты, изолированный запуск);</li>
 *   <li>privateKeyFile: приватный ключ подписи (только auth-service, хранится вне репозитория);
 *       previousPublicKeyFile: публичный ключ предыдущей пары, публикуется в JWKS на время смены ключа;</li>
 *   <li>generateIfMissing: создать ключ, если файла нет (включён только профилем dev).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @DefaultValue("warehouse-auth") String issuer,
        @DefaultValue("warehouse-api") String audience,
        @DefaultValue("15m") Duration ttl,
        String jwksUri,
        String publicKeyFile,
        String privateKeyFile,
        String previousPublicKeyFile,
        @DefaultValue("false") boolean generateIfMissing) {
}
