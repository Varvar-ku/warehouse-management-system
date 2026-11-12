package ru.warehouse.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;

/** P2-11: токен принимается, только если его audience содержит заданное значение (по умолчанию warehouse-api). */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String audience;

    public AudienceValidator(String audience) {
        this.audience = audience;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> aud = jwt.getAudience();
        if (aud != null && aud.contains(audience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Токен выдан для другого получателя (aud)", null));
    }
}
