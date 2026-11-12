package ru.warehouse.common.security;

import com.nimbusds.jwt.JWTParser;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import java.text.ParseException;
import java.util.Map;

/**
 * P2-11: выбирает декодер по издателю (iss), чтобы пользовательские токены проверялись ключом auth-service, а служебные
 * токены ключом сервиса-отправителя. Издатель читается до проверки подписи только для выбора ключа;
 * подпись и все claims затем проверяет выбранный декодер, неизвестный издатель отклоняется.
 */
public final class IssuerRoutingJwtDecoder implements JwtDecoder {
    private final Map<String, JwtDecoder> decoders;

    public IssuerRoutingJwtDecoder(Map<String, JwtDecoder> decoders) {
        this.decoders = Map.copyOf(decoders);
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer;
        try {
            issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            throw new BadJwtException("Токен не разобран", e);
        }
        JwtDecoder decoder = issuer == null ? null : decoders.get(issuer);
        if (decoder == null) {
            throw new BadJwtException("Неизвестный издатель токена");
        }
        return decoder.decode(token);
    }
}
