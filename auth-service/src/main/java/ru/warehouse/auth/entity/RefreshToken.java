package ru.warehouse.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Refresh-токен (P1-8). Само значение токена не хранится: только SHA-256 хэш. */
@Entity
@Table(name = "refresh_tokens")
@Getter @Setter
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Цепочка токенов одной сессии (входа). */
    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "family_started_at", nullable = false)
    private OffsetDateTime familyStartedAt;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    /** Когда токен обменяли на следующий (ротация). */
    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revoke_reason", length = 30)
    private String revokeReason;

    @Column(name = "replaced_by")
    private UUID replacedBy;
}
