-- Transactional outbox (как в inventory-service). Если версия V2 в warehouse-service уже занята,
-- переименуйте файл в следующую свободную до первого запуска.
CREATE TABLE outbox_event (
    id           BIGSERIAL    PRIMARY KEY,
    event_id     UUID         NOT NULL UNIQUE,
    topic        VARCHAR(100) NOT NULL,
    event_key    VARCHAR(150) NOT NULL,
    payload      JSONB        NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_event (id) WHERE published_at IS NULL;
