package ru.warehouse.request;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import ru.warehouse.common.security.ServiceTokenIssuer;
import ru.warehouse.common.security.ServiceTokenPolicy;
import ru.warehouse.common.security.JwtKeys;
import ru.warehouse.common.security.SigningKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** P1-10: политика приёма служебных токенов (ServiceTokenPolicy). Без БД и без Spring-контекста. */
class ServiceTokenPolicyTest {
    private static final SigningKey KEY = SigningKey.of((RSAPrivateCrtKey) JwtKeys.generate().getPrivate());

    private static Jwt jwt(List<String> roles, String svc) {
        Instant now = Instant.now();
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(60));
        if (roles != null) b.claim("roles", roles);
        if (svc != null) b.claim("svc", svc);
        return b.build();
    }

    private static NimbusJwtDecoder decoder(boolean acceptServiceTokens) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withPublicKey(KEY.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer("request-service"),
                new ServiceTokenPolicy(acceptServiceTokens)));
        return d;
    }

    @Test
    void userToken_isAccepted_whetherOrNotServiceTokensAreAllowed() {
        Jwt user = jwt(List.of("STOREKEEPER"), null);
        assertThat(new ServiceTokenPolicy(false).validate(user).hasErrors()).isFalse();
        assertThat(new ServiceTokenPolicy(true).validate(user).hasErrors()).isFalse();
    }

    @Test
    void tokenWithoutRoles_isAccepted() {
        assertThat(new ServiceTokenPolicy(false).validate(jwt(null, null)).hasErrors()).isFalse();
    }

    @Test
    void serviceToken_isAcceptedOnlyWhenAllowed() {
        Jwt service = jwt(List.of("SERVICE"), "request-service");
        assertThat(new ServiceTokenPolicy(true).validate(service).hasErrors()).isFalse();
        assertThat(new ServiceTokenPolicy(false).validate(service).hasErrors()).isTrue();
    }

    @Test
    void serviceRoleWithoutSvcClaim_isRejected_evenWhenAllowed() {
        assertThat(new ServiceTokenPolicy(true).validate(jwt(List.of("SERVICE"), null)).hasErrors()).isTrue();
    }

    @Test
    void svcClaimWithoutServiceRole_isRejected_evenWhenAllowed() {
        assertThat(new ServiceTokenPolicy(true).validate(jwt(List.of("ADMIN"), "request-service")).hasErrors()).isTrue();
    }

    @Test
    void serviceTokenWithExtraRoles_isRejected() {
        assertThat(new ServiceTokenPolicy(true)
                .validate(jwt(List.of("SERVICE", "ADMIN"), "request-service")).hasErrors()).isTrue();
    }

    @Test
    void realServiceToken_goesThroughDecoder_onlyWhenAllowed() {
        String token = new ServiceTokenIssuer(KEY, "request-service", "warehouse-api").issue("request-service", UUID.randomUUID());
        assertThat(decoder(true).decode(token).getClaimAsString("svc")).isEqualTo("request-service");
        assertThrows(JwtException.class, () -> decoder(false).decode(token));
    }

    @Test
    void serviceToken_hasAudienceIssuerAndKeyId() {
        String token = new ServiceTokenIssuer(KEY, "request-service", "warehouse-api").issue("request-service", UUID.randomUUID());
        Jwt jwt = decoder(true).decode(token);
        assertThat(jwt.getAudience()).containsExactly("warehouse-api");
        assertThat(jwt.getIssuer().toString()).isEqualTo("request-service");
        assertThat(jwt.getHeaders().get("kid")).isEqualTo(KEY.keyId());
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
    }

    @Test
    void issuer_refusesToSignForAnotherService() {
        ServiceTokenIssuer issuer = new ServiceTokenIssuer(KEY, "request-service", "warehouse-api");
        assertThrows(IllegalArgumentException.class, () -> issuer.issue("inventory-service", UUID.randomUUID()));
    }
}
