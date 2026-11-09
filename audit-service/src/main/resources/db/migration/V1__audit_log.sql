CREATE TABLE audit_log (
    id             BIGSERIAL    PRIMARY KEY,
    event_id       UUID         NOT NULL UNIQUE,
    schema_version INT          NOT NULL,
    source_service VARCHAR(50)  NOT NULL,
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(50)  NOT NULL,
    entity_id      VARCHAR(100) NOT NULL,
    actor_id       UUID,
    actor_role     VARCHAR(30),
    occurred_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    trace_id       VARCHAR(64),
    details        JSONB        NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_entity   ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_actor    ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_action   ON audit_log (action, occurred_at DESC);
CREATE INDEX idx_audit_occurred ON audit_log (occurred_at DESC);

-- The audit log is append-only.
CREATE FUNCTION audit_log_forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is immutable (% is not allowed)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_forbid_change();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_forbid_change();
