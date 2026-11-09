package ru.warehouse.auth.throttle;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает планировщик для очистки старых записей login_throttle. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class LoginThrottleConfig {
}
