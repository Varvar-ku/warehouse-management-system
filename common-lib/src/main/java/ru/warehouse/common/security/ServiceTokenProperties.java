package ru.warehouse.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.Map;

/**
 * P2-11: служебные токены подписываются собственным ключом вызывающего сервиса (сейчас request-service), а не ключом
 * auth-service: взлом сервиса не даёт возможности выпускать пользовательские токены.
 * <ul>
 *   <li>signingKeyFile: приватный ключ сервиса, который выпускает служебные токены (request-service);</li>
 *   <li>serviceName: имя в claim svc и issuer (по умолчанию spring.application.name);</li>
 *   <li>trusted: кто может подписывать токены, которые принимает этот сервис (inventory-service): имя сервиса и
 *       его публичные ключи (jwksUri или publicKeyFile).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security.service-token")
public record ServiceTokenProperties(
        String signingKeyFile,
        @DefaultValue("false") boolean generateIfMissing,
        String serviceName,
        @DefaultValue Map<String, TrustedIssuer> trusted) {

    public record TrustedIssuer(String jwksUri, String publicKeyFile) {}
}
