package ru.warehouse.catalog.it;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Токены для тестов: подписаны тем же секретом, что настроен в приложении (см. IntegrationTestBase). */
final class TestTokens {
    static final String SECRET = "integration-test-secret-0123456789-abcdef";
    private static final SecretKey KEY = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private static final SecretKey OTHER_KEY = new SecretKeySpec(
            "another-integration-secret-0123456789-xyz".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private TestTokens() {}

    private static String build(SecretKey key, String issuer, UUID subject, List<String> roles, String service,
                                Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject(subject.toString())
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("roles", roles);
        if (service != null) claims.claim("svc", service);
        return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    /** Обычный пользователь с ролями. */
    static String user(UUID id, String... roles) {
        Instant now = Instant.now();
        return build(KEY, "warehouse-auth", id, List.of(roles), null, now, now.plusSeconds(3600));
    }

    /** Служебный токен (как у ServiceTokenIssuer): роль SERVICE и имя вызывающего сервиса. */
    static String service(UUID actor, String serviceName) {
        Instant now = Instant.now();
        return build(KEY, "warehouse-auth", actor, List.of("SERVICE"), serviceName, now, now.plusSeconds(60));
    }

    static String expired() {
        Instant now = Instant.now();
        return build(KEY, "warehouse-auth", UUID.randomUUID(), List.of("ADMIN"), null,
                now.minusSeconds(7200), now.minusSeconds(3600));
    }

    static String wrongSignature() {
        Instant now = Instant.now();
        return build(OTHER_KEY, "warehouse-auth", UUID.randomUUID(), List.of("ADMIN"), null, now, now.plusSeconds(3600));
    }

    static String wrongIssuer() {
        Instant now = Instant.now();
        return build(KEY, "someone-else", UUID.randomUUID(), List.of("ADMIN"), null, now, now.plusSeconds(3600));
    }
}
