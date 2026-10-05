// idempotency/IdempotencyProperties.java
package ru.warehouse.inventory.idempotency;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(
        List<String> requiredPaths,        // Ant-шаблоны путей, где заголовок Idempotency-Key обязателен
        Duration inProgressTimeout,
        Duration retention) {

    public IdempotencyProperties {
        if (requiredPaths == null) {
            requiredPaths = List.of("/api/v1/reservations", "/api/v1/issues/*/confirm", "/api/v1/write-offs/*/confirm");
        }
        if (inProgressTimeout == null) inProgressTimeout = Duration.ofMinutes(2);
        if (retention == null) retention = Duration.ofHours(48);
    }
}