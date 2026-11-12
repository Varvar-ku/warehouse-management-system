package ru.warehouse.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.warehouse.auth.entity.RefreshToken;
import ru.warehouse.auth.repository.RefreshTokenRepository;
import ru.warehouse.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * P1-8: refresh-токены. Токен выдаётся при входе, при каждом обновлении заменяется новым (ротация), одноразовый.
 * <ul>
 *   <li>В базе хранится только SHA-256 хэш (токен случайный, 256 бит, поэтому соль не нужна).</li>
 *   <li>Повторное предъявление уже использованного токена вне льготного окна считается кражей:
 *       отзывается вся цепочка сессии.</li>
 *   <li>Льготное окно (reuse-grace) нужно для гонки двух вкладок: cookie общая, поэтому новый токен
 *       первая вкладка уже получила, и вторая получает только новый access-токен.</li>
 *   <li>Срок токена продлевается при обновлении, но не дальше предела сессии (max-session) от входа.</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Значение токена (отдаётся клиенту один раз, в cookie) и срок его действия. */
    public record Issued(String value, OffsetDateTime expiresAt) {}

    /** issued == null: параллельное повторное обновление в льготном окне, новый токен клиент уже получил. */
    public record Rotation(UUID userId, Issued issued) {}

    private record Created(RefreshToken entity, String raw) {}

    private final RefreshTokenRepository repository;
    private final AuthAudit audit;
    private final Duration ttl;
    private final Duration maxSession;
    private final Duration reuseGrace;

    public RefreshTokenService(RefreshTokenRepository repository, AuthAudit audit,
                               @Value("${app.security.refresh.ttl:12h}") Duration ttl,
                               @Value("${app.security.refresh.max-session:24h}") Duration maxSession,
                               @Value("${app.security.refresh.reuse-grace:10s}") Duration reuseGrace) {
        this.repository = repository;
        this.audit = audit;
        this.ttl = ttl;
        this.maxSession = maxSession;
        this.reuseGrace = reuseGrace;
    }

    /** Единый ответ для любой причины отказа: клиенту не раскрываем, почему токен не подошёл. */
    public static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Сессия недействительна или истекла. Войдите заново");
    }

    /** Новая сессия при входе. */
    @Transactional
    public Issued startSession(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        Created created = create(userId, UUID.randomUUID(), now, now);
        return new Issued(created.raw(), created.entity().getExpiresAt());
    }

    /**
     * Обмен токена на следующий. noRollbackFor: отзыв цепочки при повторном использовании должен сохраниться,
     * несмотря на ответ 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid();
        }
        OffsetDateTime now = OffsetDateTime.now();
        RefreshToken token = repository.findByHashForUpdate(hash(rawToken)).orElseThrow(RefreshTokenService::invalid);

        if (token.getRevokedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        if (token.getUsedAt() != null) {
            if (Duration.between(token.getUsedAt(), now).compareTo(reuseGrace) <= 0) {
                return new Rotation(token.getUserId(), null);
            }
            repository.revokeFamily(token.getFamilyId(), now, "REUSE");
            audit.record("auth.refresh_reuse_detected", "USER", token.getUserId(), null,
                    "familyId", token.getFamilyId().toString());
            log.warn("Повторное использование refresh-токена: пользователь {}, сессия {} отозвана",
                    token.getUserId(), token.getFamilyId());
            throw invalid();
        }
        if (!token.getFamilyStartedAt().plus(maxSession).isAfter(now)) {
            repository.revokeFamily(token.getFamilyId(), now, "MAX_SESSION");
            throw invalid();
        }

        Created next = create(token.getUserId(), token.getFamilyId(), token.getFamilyStartedAt(), now);
        token.setUsedAt(now);
        token.setReplacedBy(next.entity().getId());
        return new Rotation(token.getUserId(), new Issued(next.raw(), next.entity().getExpiresAt()));
    }

    /** Выход: отзывает цепочку сессии. Идемпотентен, неизвестный токен не считается ошибкой. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByHashForUpdate(hash(rawToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> {
                    repository.revokeFamily(t.getFamilyId(), OffsetDateTime.now(), "LOGOUT");
                    audit.record("auth.logout", "USER", t.getUserId(), t.getUserId(), "reason", "LOGOUT");
                });
    }

    /** Отзыв всех сессий пользователя: блокировка, смена ролей, смена и сброс пароля. */
    @Transactional
    public int revokeAllForUser(UUID userId, String reason) {
        int revoked = repository.revokeAllForUser(userId, OffsetDateTime.now(), reason);
        if (revoked > 0) {
            log.info("Отозвано refresh-токенов пользователя {}: {} ({})", userId, revoked, reason);
        }
        return revoked;
    }

    /** Токены, истёкшие более суток назад, не нужны даже для расследований. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT2M")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpiredBefore(OffsetDateTime.now().minusDays(1));
        if (deleted > 0) {
            log.debug("Удалено устаревших refresh-токенов: {}", deleted);
        }
    }

    private Created create(UUID userId, UUID familyId, OffsetDateTime familyStart, OffsetDateTime now) {
        String raw = newTokenValue();
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setFamilyId(familyId);
        t.setFamilyStartedAt(familyStart);
        t.setTokenHash(hash(raw));
        t.setCreatedAt(now);
        OffsetDateTime byTtl = now.plus(ttl);
        OffsetDateTime byMaxSession = familyStart.plus(maxSession);
        t.setExpiresAt(byTtl.isBefore(byMaxSession) ? byTtl : byMaxSession);
        RefreshToken saved = repository.save(t);
        return new Created(saved, raw);
    }

    private static String newTokenValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }
}
