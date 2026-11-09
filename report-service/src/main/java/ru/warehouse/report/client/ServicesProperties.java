package ru.warehouse.report.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.services")
public record ServicesProperties(String authUrl) {
}
