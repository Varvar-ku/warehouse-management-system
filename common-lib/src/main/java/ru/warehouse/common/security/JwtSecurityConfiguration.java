package ru.warehouse.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.core.env.Environment;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Защита сервиса: все запросы требуют валидный JWT, кроме health и путей из app.security.public-paths.
 * Роли берутся из claim "roles" и получают префикс ROLE_, поэтому работает @PreAuthorize("hasRole('ADMIN')").
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity // включает @PreAuthorize в контроллерах и сервисах
@EnableConfigurationProperties({JwtProperties.class, WarehouseSecurityProperties.class, ServiceTokenProperties.class})
public class JwtSecurityConfiguration {

    /**
     * P2-11: токены подписаны RS256. Пользовательские токены проверяются публичным ключом auth-service (JWKS по сети,
     * либо локально: у самого auth-service есть свой ключ, а в тестах публичный ключ задан файлом). Дополнительно
     * проверяются издатель, audience и политика служебных токенов (P1-10). Сервис, который принимает служебные токены
     * (app.security.accept-service-tokens=true, сейчас только inventory-service), добавляет к этому проверку подписи
     * сервиса-отправителя по его собственному ключу.
     */
    @Bean
    JwtDecoder jwtDecoder(JwtProperties props, ServiceTokenProperties serviceTokens, Environment env,
                          @Qualifier("userTokenSigningKey") ObjectProvider<SigningKey> localUserKey) {
        // издатель auth-service: служебная роль в таком токене недопустима
        OAuth2TokenValidator<Jwt> userValidator = new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefaultWithIssuer(props.issuer()),
                new AudienceValidator(props.audience()),
                new ServiceTokenPolicy(false));
        SigningKey local = localUserKey.getIfAvailable();
        JwtDecoder users;
        if (local != null) {
            users = staticKeyDecoder(local.verificationKeys(), userValidator);
        } else {
            users = decoderFor("app.security.jwt", props.jwksUri(), props.publicKeyFile(), userValidator);
        }

        boolean acceptServiceTokens = env.getProperty("app.security.accept-service-tokens", Boolean.class, false);
        if (!acceptServiceTokens) {
            return users;
        }
        if (serviceTokens.trusted().isEmpty()) {
            throw new IllegalStateException("app.security.accept-service-tokens=true, но не задан ни один сервис в "
                    + "app.security.service-token.trusted (имя сервиса и его jwks-uri или public-key-file).");
        }
        Map<String, JwtDecoder> byIssuer = new LinkedHashMap<>();
        byIssuer.put(props.issuer(), users);
        serviceTokens.trusted().forEach((name, trusted) -> {
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<Jwt>(
                    JwtValidators.createDefaultWithIssuer(name),
                    new AudienceValidator(props.audience()),
                    new JwtClaimValidator<String>(ServiceTokenIssuer.SERVICE_CLAIM, name::equals),
                    new ServiceTokenPolicy(true));
            byIssuer.put(name, decoderFor("app.security.service-token.trusted." + name,
                    trusted.jwksUri(), trusted.publicKeyFile(), validator));
        });
        return new IssuerRoutingJwtDecoder(byIssuer);
    }

    /** Ключ подписи пользовательских токенов: только у auth-service (задан app.security.jwt.private-key-file). */
    @Bean(name = "userTokenSigningKey")
    @ConditionalOnProperty(prefix = "app.security.jwt", name = "private-key-file")
    SigningKey userTokenSigningKey(JwtProperties props) {
        var key = JwtKeys.loadOrCreatePrivateKey(props.privateKeyFile(), props.generateIfMissing(), "ключ подписи JWT");
        List<RSAPublicKey> previous = new ArrayList<>();
        if (props.previousPublicKeyFile() != null && !props.previousPublicKeyFile().isBlank()) {
            previous.add(JwtKeys.readPublicKey(Path.of(props.previousPublicKeyFile())));
        }
        return SigningKey.of(key, previous);
    }

    /** Ключ подписи служебных токенов: только у сервиса, который их выпускает (request-service). */
    @Bean(name = "serviceTokenSigningKey")
    @ConditionalOnProperty(prefix = "app.security.service-token", name = "signing-key-file")
    SigningKey serviceTokenSigningKey(ServiceTokenProperties props) {
        return SigningKey.of(JwtKeys.loadOrCreatePrivateKey(
                props.signingKeyFile(), props.generateIfMissing(), "ключ служебных токенов"));
    }

    /** P1-10: выпуск служебных токенов для вызовов между сервисами. */
    @Bean
    @ConditionalOnProperty(prefix = "app.security.service-token", name = "signing-key-file")
    ServiceTokenIssuer serviceTokenIssuer(@Qualifier("serviceTokenSigningKey") SigningKey key,
                                          ServiceTokenProperties serviceTokens, JwtProperties props, Environment env) {
        String name = serviceTokens.serviceName() != null && !serviceTokens.serviceName().isBlank()
                ? serviceTokens.serviceName() : env.getProperty("spring.application.name");
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Не задано имя сервиса (spring.application.name) для служебных токенов");
        }
        return new ServiceTokenIssuer(key, name, props.audience());
    }

    private static JwtDecoder decoderFor(String property, String jwksUri, String publicKeyFile,
                                         OAuth2TokenValidator<Jwt> validator) {
        if (publicKeyFile != null && !publicKeyFile.isBlank()) {
            return staticKeyDecoder(List.of(JwtKeys.readPublicKey(Path.of(publicKeyFile))), validator);
        }
        if (jwksUri != null && !jwksUri.isBlank()) {
            return jwksDecoder(jwksUri, validator);
        }
        throw new IllegalStateException("Не задан источник публичного ключа: укажите " + property
                + (property.endsWith("jwt") ? ".jwks-uri" : ".jwks-uri или .public-key-file")
                + " (адрес JWKS auth-service или файл публичного ключа).");
    }

    /** Публичные ключи берутся с адреса JWKS при первом токене и кэшируются; при неизвестном kid перечитываются. */
    private static JwtDecoder jwksDecoder(String jwksUri, OAuth2TokenValidator<Jwt> validator) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));   // недоступный источник ключей не должен держать потоки
        factory.setReadTimeout(Duration.ofSeconds(5));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                .build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Фиксированный набор публичных ключей без сетевых запросов (auth-service, тесты). Алгоритм только RS256. */
    private static JwtDecoder staticKeyDecoder(List<RSAPublicKey> keys, OAuth2TokenValidator<Jwt> validator) {
        List<JWK> jwks = new ArrayList<>();
        for (RSAPublicKey key : keys) {
            jwks.add(new RSAKey.Builder(key).keyID(JwtKeys.keyId(key)).build());
        }
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                new ImmutableJWKSet<>(new JWKSet(jwks))));
        processor.setJWTClaimsSetVerifier((claims, context) -> { });   // сроки и claims проверяют валидаторы Spring
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder,
                                            JwtAuthenticationConverter converter,
                                            WarehouseSecurityProperties props) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            JsonErrorWriter.write(response, 401, "UNAUTHORIZED",
                    "Требуется аутентификация или токен недействителен");
        };
        AccessDeniedHandler deniedHandler = (request, response, ex) ->
                JsonErrorWriter.write(response, 403, "FORBIDDEN", "Недостаточно прав для выполнения операции");

        String[] publicPaths = props.publicPaths().toArray(String[]::new);

        http
                .csrf(AbstractHttpConfigurer::disable)          // API без cookie-сессий, токен в заголовке
                .cors(Customizer.withDefaults())                // CORS настраивает gateway (bean CorsConfigurationSource)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    auth.requestMatchers("/actuator/**").hasRole("ADMIN");   // metrics и прочее: только администратор
                    auth.requestMatchers("/error").permitAll(); // иначе ошибки контейнера превращаются в 401
                    if (publicPaths.length > 0) {
                        auth.requestMatchers(publicPaths).permitAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)));
        return http.build();
    }
}