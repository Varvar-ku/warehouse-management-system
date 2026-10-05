package ru.warehouse.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import ru.warehouse.auth.entity.Role;
import ru.warehouse.auth.entity.User;
import ru.warehouse.common.security.JwtProperties;
import java.time.Instant;
import java.util.List;

/** Выпуск access-токена. Другие сервисы проверяют его по тому же секрету. */
@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties props;

    public record IssuedToken(String value, long expiresInSeconds) {}

    /** Вызывать внутри транзакции: роли пользователя читаются лениво. */
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(props.ttl());
        List<String> roleCodes = user.getRoles().stream().map(Role::getCode).sorted().toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(user.getId().toString())   // sub = id пользователя
                .issuedAt(now)
                .expiresAt(expires)
                .claim("login", user.getLogin())
                .claim("roles", roleCodes)          // из этого claim сервисы строят ROLE_*
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, props.ttl().toSeconds());
    }
}