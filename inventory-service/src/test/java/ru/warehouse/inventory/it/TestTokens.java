package ru.warehouse.inventory.it;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import ru.warehouse.common.security.JwtKeys;
import ru.warehouse.common.security.SigningKey;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Токены для тестов (P2-11: RS256). Тестовая пара ключей создаётся один раз на запуск и записывается во временные
 * файлы, пути к которым IntegrationTestBase передаёт приложению (публичный ключ для проверки токенов,
 * приватный для auth-service и для служебных токенов request-service). Токены подписаны этой же парой.
 */
final class TestTokens {
    static final String ISSUER = "warehouse-auth";
    static final String AUDIENCE = "warehouse-api";
    /** Имя сервиса, который выпускает служебные токены (issuer и claim svc). */
    static final String SERVICE_ISSUER = "request-service";

    private static final KeyPair PAIR = JwtKeys.generate();
    private static final SigningKey KEY = SigningKey.of((RSAPrivateCrtKey) PAIR.getPrivate());
    private static final SigningKey OTHER_KEY = SigningKey.of((RSAPrivateCrtKey) JwtKeys.generate().getPrivate());
    private static final Path PRIVATE_FILE;
    private static final Path PUBLIC_FILE;

    static {
        try {
            Path dir = Files.createTempDirectory("wms-test-keys");
            dir.toFile().deleteOnExit();
            PRIVATE_FILE = dir.resolve("private.pem");
            PUBLIC_FILE = dir.resolve("public.pem");
            JwtKeys.writePrivateKey(PRIVATE_FILE, PAIR.getPrivate());
            JwtKeys.writePublicKey(PUBLIC_FILE, PAIR.getPublic());
            PRIVATE_FILE.toFile().deleteOnExit();
            PUBLIC_FILE.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private TestTokens() {}

    static String publicKeyFile() {
        return PUBLIC_FILE.toString();
    }

    static String privateKeyFile() {
        return PRIVATE_FILE.toString();
    }

    private static String build(SigningKey key, String issuer, String audience, UUID subject, List<String> roles,
                                String service, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject(subject.toString())
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("roles", roles);
        if (audience != null) claims.audience(List.of(audience));
        if (service != null) claims.claim("svc", service);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.keyId()).build();
        return key.encoder().encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    /** Обычный пользователь с ролями. */
    static String user(UUID id, String... roles) {
        Instant now = Instant.now();
        return build(KEY, ISSUER, AUDIENCE, id, List.of(roles), null, now, now.plusSeconds(3600));
    }

    /** Служебный токен (как у ServiceTokenIssuer): роль SERVICE, issuer и svc равны имени вызывающего сервиса. */
    static String service(UUID actor, String serviceName) {
        Instant now = Instant.now();
        return build(KEY, serviceName, AUDIENCE, actor, List.of("SERVICE"), serviceName, now, now.plusSeconds(60));
    }

    static String expired() {
        Instant now = Instant.now();
        return build(KEY, ISSUER, AUDIENCE, UUID.randomUUID(), List.of("ADMIN"), null,
                now.minusSeconds(7200), now.minusSeconds(3600));
    }

    static String wrongSignature() {
        Instant now = Instant.now();
        return build(OTHER_KEY, ISSUER, AUDIENCE, UUID.randomUUID(), List.of("ADMIN"), null, now, now.plusSeconds(3600));
    }

    static String wrongIssuer() {
        Instant now = Instant.now();
        return build(KEY, "someone-else", AUDIENCE, UUID.randomUUID(), List.of("ADMIN"), null, now, now.plusSeconds(3600));
    }

    /** Подпись и издатель верные, но токен выдан для другого получателя (P2-11). */
    static String wrongAudience() {
        Instant now = Instant.now();
        return build(KEY, ISSUER, "another-api", UUID.randomUUID(), List.of("ADMIN"), null, now, now.plusSeconds(3600));
    }

    /** Токен без audience (P2-11). */
    static String withoutAudience() {
        Instant now = Instant.now();
        return build(KEY, ISSUER, null, UUID.randomUUID(), List.of("ADMIN"), null, now, now.plusSeconds(3600));
    }
}
