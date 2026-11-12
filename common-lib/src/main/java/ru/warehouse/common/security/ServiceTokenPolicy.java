package ru.warehouse.common.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;

/**
 * P1-10: политика приёма служебных токенов. Служебный токен (роль SERVICE и claim "svc") нужен только для вызовов
 * между сервисами, поэтому:
 * <ul>
 *   <li>токен, у которого роль SERVICE и claim svc не согласованы, отклоняется (пользователь не может получить
 *       служебные права, а служебный токен не может нести пользовательские роли);</li>
 *   <li>служебный токен принимает только сервис, который явно это разрешил (app.security.accept-service-tokens=true,
 *       сейчас только inventory-service); gateway, auth и остальные такие токены отклоняют с 401.</li>
 * </ul>
 */
public final class ServiceTokenPolicy implements OAuth2TokenValidator<Jwt> {
    private final boolean acceptServiceTokens;

    public ServiceTokenPolicy(boolean acceptServiceTokens) {
        this.acceptServiceTokens = acceptServiceTokens;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        boolean hasServiceClaim = jwt.hasClaim(ServiceTokenIssuer.SERVICE_CLAIM);
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean hasServiceRole = roles != null && roles.contains(ServiceTokenIssuer.ROLE);

        if (hasServiceClaim != hasServiceRole) {
            return failure("Роль SERVICE и claim svc должны присутствовать вместе");
        }
        if (hasServiceClaim) {
            if (roles.size() != 1) {
                return failure("Служебный токен не может содержать другие роли");
            }
            if (!acceptServiceTokens) {
                return failure("Этот сервис не принимает служебные токены");
            }
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }
}
