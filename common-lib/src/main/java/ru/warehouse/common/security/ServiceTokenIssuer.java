package ru.warehouse.common.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Служебный токен для вызовов между сервисами (P1-10). Короткоживущий (60 с), выпускается на каждый вызов.
 * Содержит роль SERVICE (её нет ни у одного пользователя), имя вызывающего сервиса в claim "svc" и
 * в subject пользователя, от имени которого выполняется действие: аудит и журналы сохраняют настоящего исполнителя.
 * Принимающий сервис проверяет и роль, и имя сервиса (см. @RequestServiceOnly в inventory-service).
 */
public class ServiceTokenIssuer {
    public static final String ROLE = "SERVICE";
    public static final String SERVICE_CLAIM = "svc";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final JwtEncoder encoder;
    private final String issuer;

    public ServiceTokenIssuer(SecretKey key, String issuer) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.issuer = issuer;
    }

    /** serviceName: имя вызывающего сервиса (например, request-service); actorId: пользователь, от имени которого действие. */
    public String issue(String serviceName, UUID actorId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(actorId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(ROLE))
                .claim(SERVICE_CLAIM, serviceName)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
