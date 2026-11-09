package ru.warehouse.auth.throttle;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Настройки защиты входа от перебора: app.security.login-throttle.*
 * Пара (логин + IP) блокируется после pairMaxFailures неудач, весь IP после ipMaxFailures.
 * Пауза растёт вдвое с каждой следующей неудачей, но не больше maxLock.
 * Счётчик обнуляется, если за resetAfter не было неудачных попыток.
 */
@ConfigurationProperties(prefix = "app.security.login-throttle")
public record LoginThrottleProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int pairMaxFailures,
        @DefaultValue("30s") Duration pairBaseLock,
        @DefaultValue("30") int ipMaxFailures,
        @DefaultValue("60s") Duration ipBaseLock,
        @DefaultValue("15m") Duration maxLock,
        @DefaultValue("15m") Duration resetAfter) {
}
