CREATE TABLE notifications (
    id           UUID          PRIMARY KEY,
    recipient_id UUID          NOT NULL,
    type         VARCHAR(40)   NOT NULL,
    title        VARCHAR(200)  NOT NULL,
    message      VARCHAR(1000) NOT NULL,
    request_id   UUID,
    event_id     UUID          NOT NULL,
    trace_id     VARCHAR(64),
    created_at   TIMESTAMPTZ   NOT NULL,
    read_at      TIMESTAMPTZ,
    CONSTRAINT uq_notifications_event_recipient UNIQUE (event_id, recipient_id),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'REQUEST_APPROVED', 'REQUEST_PARTIALLY_AVAILABLE', 'REQUEST_RESERVED',
        'REQUEST_RESERVED_PARTIAL', 'REQUEST_READY_FOR_ISSUE', 'REQUEST_ISSUED'))
);

CREATE INDEX idx_notifications_recipient_created
    ON notifications (recipient_id, created_at DESC);

CREATE INDEX idx_notifications_recipient_unread
    ON notifications (recipient_id) WHERE read_at IS NULL;
