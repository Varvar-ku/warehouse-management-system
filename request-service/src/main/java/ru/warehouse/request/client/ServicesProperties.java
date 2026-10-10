// ServicesProperties.java
package ru.warehouse.request.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

//@ConfigurationProperties(prefix = "app.services")
//public record ServicesProperties(String catalogUrl, String warehouseUrl, String inventoryUrl) {}

@ConfigurationProperties(prefix = "app.services")
public record ServicesProperties(String catalogUrl, String warehouseUrl, String inventoryUrl, String authUrl) {}