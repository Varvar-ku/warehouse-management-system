package ru.warehouse.request.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.warehouse.common.security.SigningKey;
import java.time.Duration;

/**
 * P2-11: публичный ключ, которым request-service подписывает служебные токены (их принимает inventory-service).
 * Путь открыт без токена (app.security.public-paths), через gateway не проброшен, приватных данных нет.
 */
@RestController
public class JwksController {
    private final SigningKey key;

    public JwksController(@Qualifier("serviceTokenSigningKey") SigningKey key) {
        this.key = key;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(key.jwksJson());
    }
}
