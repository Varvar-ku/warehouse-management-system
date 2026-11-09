package ru.warehouse.request;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import ru.warehouse.common.security.ServiceTokenIssuer;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenIssuerTest {
    private static final SecretKey KEY = new SecretKeySpec(
            "test-secret-0123456789abcdef-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    private static JwtDecoder decoder() {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(JwtValidators.createDefaultWithIssuer("warehouse-auth")); // как в JwtSecurityConfiguration
        return d;
    }

    @Test
    void token_isAcceptedByServiceDecoder_andCarriesServiceIdentityAndActor() {
        UUID actor = UUID.randomUUID();
        String token = new ServiceTokenIssuer(KEY, "warehouse-auth").issue("request-service", actor);

        Jwt jwt = decoder().decode(token);
        assertThat(jwt.getSubject()).isEqualTo(actor.toString());
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("SERVICE"));
        assertThat(jwt.getClaimAsString("svc")).isEqualTo("request-service");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void token_isShortLived() {
        Jwt jwt = decoder().decode(new ServiceTokenIssuer(KEY, "warehouse-auth").issue("request-service", UUID.randomUUID()));
        Duration ttl = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofMinutes(2));
        assertThat(jwt.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void tokenSignedWithOtherKey_isRejected() {
        SecretKey other = new SecretKeySpec("another-secret-0123456789abcdef-xx".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        String forged = new ServiceTokenIssuer(other, "warehouse-auth").issue("request-service", UUID.randomUUID());
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,
                () -> decoder().decode(forged));
    }
}
