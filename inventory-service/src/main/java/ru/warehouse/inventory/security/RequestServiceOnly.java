package ru.warehouse.inventory.security;

import org.springframework.security.access.prepost.PreAuthorize;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Внутренняя операция: её вызывает только request-service со служебным токеном (роль SERVICE и claim svc).
 * Ни кладовщик, ни администратор вызвать её не могут (P1-10, R5): заявкой владеет request-service,
 * резерв и выдача создаются только в рамках её жизненного цикла.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasRole('SERVICE') and authentication.token.claims['svc'] == 'request-service'")
public @interface RequestServiceOnly {}
