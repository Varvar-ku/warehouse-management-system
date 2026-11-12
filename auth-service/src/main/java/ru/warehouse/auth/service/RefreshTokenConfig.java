package ru.warehouse.auth.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает планировщик для очистки старых refresh-токенов (RefreshTokenService.purgeExpired). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class RefreshTokenConfig {
}
