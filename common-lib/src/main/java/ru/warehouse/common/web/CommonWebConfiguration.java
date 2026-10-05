package ru.warehouse.common.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.warehouse.common.exception.GlobalExceptionHandler;
import ru.warehouse.common.exception.PersistenceExceptionHandler;

@Configuration(proxyBeanMethods = false)
public class CommonWebConfiguration {

    @Bean
    TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /** Обработчик ошибок БД регистрируется только если в приложении есть JPA (spring-orm). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.orm.ObjectOptimisticLockingFailureException")
    static class PersistenceErrors {
        @Bean
        PersistenceExceptionHandler persistenceExceptionHandler() {
            return new PersistenceExceptionHandler();
        }
    }
}