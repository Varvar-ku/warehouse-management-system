package ru.warehouse.auth.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import ru.warehouse.auth.entity.Role;
import ru.warehouse.auth.entity.User;
import ru.warehouse.common.security.JwtProperties;
import ru.warehouse.common.security.SigningKey;
import java.time.Instant;
import java.util.List;

/**
 * Выпуск access-токена (P2-11: RS256, audience). Другие сервисы проверяют его публичным ключом из JWKS auth-service
 * (GET /.well-known/jwks.json), приватный ключ есть только здесь.
 */
@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final SigningKey key;
    private final JwtProperties props;

    public TokenService(@Qualifier("userTokenSigningKey") SigningKey key, JwtProperties props) {
        this.key = key;
        this.encoder = key.encoder();
        this.props = props;
    }

    public record IssuedToken(String value, long expiresInSeconds) {}

    /** Вызывать внутри транзакции: роли пользователя читаются лениво. */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(props.ttl());
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .audience(List.of(props.audience()))   // получатель: сервисы WMS; токен для другого получателя отклоняется
                .subject(user.getId().toString())      // sub = id пользователя
                .issuedAt(now)
                .expiresAt(expires)
                .claim("login", user.getLogin())
                .claim("roles", roleCodes)             // из этого claim сервисы строят ROLE_*
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(key.keyId()).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, props.ttl().toSeconds());
    }
}
