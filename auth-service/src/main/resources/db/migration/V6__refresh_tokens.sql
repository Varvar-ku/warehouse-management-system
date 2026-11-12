-- P1-8: refresh-токены с ротацией и отзывом. В базе только SHA-256 хэш токена.
-- family_id объединяет цепочку токенов одной сессии: при повторном предъявлении уже использованного токена
-- отзывается вся цепочка. family_started_at нужен для абсолютного предела сессии (app.security.refresh.max-session).
CREATE TABLE refresh_tokens (
    id                UUID         PRIMARY KEY,
    user_id           UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id         UUID         NOT NULL,
    family_started_at TIMESTAMPTZ  NOT NULL,
    token_hash        VARCHAR(64)  NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    used_at           TIMESTAMPTZ,
    revoked_at        TIMESTAMPTZ,
    revoke_reason     VARCHAR(30),
    replaced_by       UUID
);

CREATE INDEX idx_refresh_tokens_family  ON refresh_tokens (family_id);
CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);
