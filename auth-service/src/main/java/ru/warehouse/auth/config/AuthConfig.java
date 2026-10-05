package ru.warehouse.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import ru.warehouse.common.security.JwtKeys;
import ru.warehouse.common.security.JwtProperties;

@Configuration
public class AuthConfig {

    /** BCrypt: современный адаптивный алгоритм хэширования паролей (разд. 18 ТЗ). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Подпись токенов тем же секретом, которым другие сервисы их проверяют. */
    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(JwtKeys.secretKey(props)));
    }
}