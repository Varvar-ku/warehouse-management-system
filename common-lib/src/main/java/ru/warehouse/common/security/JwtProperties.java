package ru.warehouse.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

/** Настройки JWT: app.security.jwt.* (secret, issuer, ttl). ttl используется только auth-service. */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String secret,
        @DefaultValue("warehouse-auth") String issuer,
        @DefaultValue("8h") Duration ttl) {
}