package ru.warehouse.inventory.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import ru.warehouse.inventory.controller.IssueController;
import ru.warehouse.inventory.controller.ReservationController;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1-10. Без БД и без запуска приложения.
 * 1) Проверяет выражение @RequestServiceOnly на настоящем методном контроле доступа Spring Security.
 * 2) Следит, чтобы любая изменяющая операция в ReservationController и IssueController была помечена
 *    @RequestServiceOnly (новый метод без аннотации сломает этот тест).
 */
class RequestServiceOnlyTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        Guarded guarded() {
            return new Guarded();
        }
    }

    public static class Guarded {
        @RequestServiceOnly
        public String act() {
            return "ok";
        }
    }

    private AnnotationConfigApplicationContext ctx;
    private Guarded guarded;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        guarded = ctx.getBean(Guarded.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        ctx.close();
    }

    private static void loginAs(List<String> roles, String svc) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("roles", roles);
        if (svc != null) {
            builder.claim("svc", svc);
        }
        List<GrantedAuthority> authorities = roles.stream()
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), authorities));
    }

    @Test
    void requestService_isAllowed() {
        loginAs(List.of("SERVICE"), "request-service");
        assertThat(guarded.act()).isEqualTo("ok");
    }

    @Test
    void storekeeper_isDenied() {
        loginAs(List.of("STOREKEEPER"), null);
        assertThrows(AccessDeniedException.class, () -> guarded.act());
    }

    @Test
    void admin_isDenied() {
        loginAs(List.of("ADMIN"), null);
        assertThrows(AccessDeniedException.class, () -> guarded.act());
    }

    @Test
    void storekeeperAndAdmin_together_areDenied() {
        loginAs(List.of("STOREKEEPER", "ADMIN"), null);
        assertThrows(AccessDeniedException.class, () -> guarded.act());
    }

    @Test
    void otherService_isDenied() {
        loginAs(List.of("SERVICE"), "report-service");
        assertThrows(AccessDeniedException.class, () -> guarded.act());
    }

    @Test
    void serviceRoleWithoutSvcClaim_isDenied() {
        loginAs(List.of("SERVICE"), null);
        assertThrows(AccessDeniedException.class, () -> guarded.act());
    }

    @Test
    void svcClaimWithoutServiceRole_isDenied() {
        loginAs(List.of("STOREKEEPER"), "request-service");
        assertThrows(AccessDeniedException.class, () -> guarded.act());
    }

    @Test
    void everyMutatingEndpointOfInternalControllers_isRequestServiceOnly() {
        // POST, которые только читают данные и доступны кладовщику
        Set<String> readOnlyPosts = Set.of("availability");
        int checked = 0;
        for (Class<?> controller : List.of(ReservationController.class, IssueController.class)) {
            for (Method m : controller.getDeclaredMethods()) {
                boolean mutating = AnnotatedElementUtils.hasAnnotation(m, PostMapping.class)
                        || AnnotatedElementUtils.hasAnnotation(m, PutMapping.class)
                        || AnnotatedElementUtils.hasAnnotation(m, PatchMapping.class)
                        || AnnotatedElementUtils.hasAnnotation(m, DeleteMapping.class);
                if (!mutating || readOnlyPosts.contains(m.getName())) {
                    continue;
                }
                checked++;
                assertThat(AnnotatedElementUtils.hasAnnotation(m, RequestServiceOnly.class))
                        .as("%s.%s должен быть помечен @RequestServiceOnly", controller.getSimpleName(), m.getName())
                        .isTrue();
            }
        }
        // reserve, release, create, confirm, cancel
        assertThat(checked).isGreaterThanOrEqualTo(5);
    }
}
