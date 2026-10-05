package ru.warehouse.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Защита сервиса: все запросы требуют валидный JWT, кроме health и путей из app.security.public-paths.
 * Роли берутся из claim "roles" и получают префикс ROLE_, поэтому работает @PreAuthorize("hasRole('ADMIN')").
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity // включает @PreAuthorize в контроллерах и сервисах
@EnableConfigurationProperties({JwtProperties.class, WarehouseSecurityProperties.class})
public class JwtSecurityConfiguration {

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(JwtKeys.secretKey(props))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Кроме подписи и срока действия проверяем издателя токена
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
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