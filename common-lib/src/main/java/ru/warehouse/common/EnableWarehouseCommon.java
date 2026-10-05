package ru.warehouse.common;

import org.springframework.context.annotation.Import;
import ru.warehouse.common.security.JwtSecurityConfiguration;
import ru.warehouse.common.web.CommonWebConfiguration;
import java.lang.annotation.*;

/**
 * Включает общую инфраструктуру сервиса одной строкой на главном классе:
 * единый формат ошибок, trace-id и защиту по JWT.
 * Это обычный @Import (а не автоконфигурация), поэтому порядок инициализации предсказуем:
 * наша цепочка безопасности создаётся раньше стандартной и заменяет её.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({CommonWebConfiguration.class, JwtSecurityConfiguration.class})
public @interface EnableWarehouseCommon {
}