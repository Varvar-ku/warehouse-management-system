package ru.warehouse.common.security;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Служебный токен для вызовов между сервисами (P1-10). Короткоживущий (60 с), выпускается на каждый вызов.
 * Содержит роль SERVICE (её нет ни у одного пользователя), имя вызывающего сервиса в claim "svc" и
 * в subject пользователя, от имени которого выполняется действие: аудит и журналы сохраняют настоящего исполнителя.
 * Принимающий сервис проверяет и роль, и имя сервиса (см. @RequestServiceOnly в inventory-service).
 *
 * P2-11: токен подписывается собственным ключом сервиса (RS256), issuer равен имени сервиса, audience общий.
 * Принимающий сервис знает публичный ключ отправителя (app.security.service-token.trusted), а не общий секрет.
 */
public class ServiceTokenIssuer {
    public static final String ROLE = "SERVICE";
    public static final String SERVICE_CLAIM = "svc";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final JwtEncoder encoder;
    private final String keyId;
    private final String serviceName;
    private final String audience;

    public ServiceTokenIssuer(SigningKey key, String serviceName, String audience) {
        this.encoder = key.encoder();
        this.keyId = key.keyId();
        this.serviceName = serviceName;
        this.audience = audience;
    }

    /**
     * serviceName: имя вызывающего сервиса (например, request-service), должно совпадать с именем этого сервиса;
     * actorId: пользователь, от имени которого выполняется действие.
     */
    public String issue(String serviceName, UUID actorId) {
        if (!this.serviceName.equals(serviceName)) {
            throw new IllegalArgumentException("Этот сервис подписывает токены только как " + this.serviceName);
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(this.serviceName)
                .audience(List.of(audience))
                .subject(actorId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(ROLE))
                .claim(SERVICE_CLAIM, this.serviceName)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
