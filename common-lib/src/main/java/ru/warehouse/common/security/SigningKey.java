package ru.warehouse.common.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

/** P2-11: пара ключей для подписи RS256 с идентификатором kid; публичная часть отдаётся через JWKS. */
public final class SigningKey {
    private final RSAPrivateCrtKey privateKey;
    private final RSAPublicKey publicKey;
    private final String keyId;
    private final List<RSAPublicKey> previousPublicKeys;

    private SigningKey(RSAPrivateCrtKey privateKey, List<RSAPublicKey> previousPublicKeys) {
        this.privateKey = privateKey;
        this.publicKey = JwtKeys.publicKeyOf(privateKey);
        this.keyId = JwtKeys.keyId(publicKey);
        this.previousPublicKeys = List.copyOf(previousPublicKeys);
    }

    /** previousPublicKeys: ключи предыдущих пар (только для публикации в JWKS при смене ключа). */
    public static SigningKey of(RSAPrivateCrtKey privateKey, List<RSAPublicKey> previousPublicKeys) {
        return new SigningKey(privateKey, previousPublicKeys);
    }

    public static SigningKey of(RSAPrivateCrtKey privateKey) {
        return new SigningKey(privateKey, List.of());
    }

    public String keyId() {
        return keyId;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public List<RSAPublicKey> previousPublicKeys() {
        return previousPublicKeys;
    }

    /** Все публичные ключи, которыми можно проверить токен: текущий и предыдущие. */
    public List<RSAPublicKey> verificationKeys() {
        List<RSAPublicKey> all = new ArrayList<>();
        all.add(publicKey);
        all.addAll(previousPublicKeys);
        return all;
    }

    public JwtEncoder encoder() {
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(privateKey)
                .keyID(keyId).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    /** JWKS-документ только с публичными ключами (приватная часть в него не попадает). */
    public String jwksJson() {
        List<JWK> keys = new ArrayList<>();
        for (RSAPublicKey key : verificationKeys()) {
            keys.add(new RSAKey.Builder(key).keyID(JwtKeys.keyId(key))
                    .keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build());
        }
        return new JWKSet(keys).toString();
    }
}
