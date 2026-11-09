-- R12/M3: идемпотентность запросов в request-service (тот же механизм, что в inventory-service).
-- owner: токен попытки; только владелец может завершить или освободить ключ.
CREATE TABLE idempotency_keys (
    user_id         UUID         NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    fingerprint     VARCHAR(64)  NOT NULL,                 -- SHA-256 от метода, пути, запроса и тела
    status          VARCHAR(20)  NOT NULL,                 -- IN_PROGRESS / COMPLETED
    response_status INT,
    response_body   TEXT,
    owner           UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMPTZ,
    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);
