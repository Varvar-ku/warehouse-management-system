package ru.warehouse.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.List;

/** app.security.public-paths: пути без аутентификации (например, /api/v1/auth/login). */
@ConfigurationProperties(prefix = "app.security")
public record WarehouseSecurityProperties(@DefaultValue List<String> publicPaths) {
}