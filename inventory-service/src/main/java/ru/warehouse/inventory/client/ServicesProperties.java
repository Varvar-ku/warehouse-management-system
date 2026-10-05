// ServicesProperties.java
package ru.warehouse.inventory.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Адреса соседних сервисов: app.services.* */
@ConfigurationProperties(prefix = "app.services")
public record ServicesProperties(String catalogUrl, String warehouseUrl) {}