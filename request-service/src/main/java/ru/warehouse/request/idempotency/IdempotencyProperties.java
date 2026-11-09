package ru.warehouse.request.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

/**
 * Настройки идемпотентности (app.idempotency.*).
 * requiredPaths: шаблоны Ant, для которых заголовок Idempotency-Key обязателен (по умолчанию создание заявки).
 * inProgressTimeout: сколько запись без «сердцебиения» считается брошенной (процесс упал) и может быть перехвачена.
 * retention: сколько хранить записи.
 */
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(List<String> requiredPaths, Duration inProgressTimeout, Duration retention) {
    public IdempotencyProperties {
        if (requiredPaths == null) requiredPaths = List.of("/api/v1/requests"); // создание заявки: ключ обязателен
        if (inProgressTimeout == null) inProgressTimeout = Duration.ofMinutes(2);
        if (retention == null) retention = Duration.ofHours(24);
    }
}
