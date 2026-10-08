CREATE SEQUENCE request_number_seq START 1;

CREATE TABLE requests (
                          id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          lock_version BIGINT      NOT NULL DEFAULT 0,
                          number       VARCHAR(50) NOT NULL UNIQUE,
                          requester_id UUID        NOT NULL,                  -- пользователь принадлежит auth-service
                          warehouse_id UUID        NOT NULL,                  -- в ТЗ поля нет, но склад нужен для резерва
                          status       VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
                          priority     INT         NOT NULL DEFAULT 0,
                          comment      TEXT,
                          status_reason TEXT,                                  -- причина отклонения или отмены
                          created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          submitted_at TIMESTAMPTZ,
                          reviewed_by  UUID,
                          reviewed_at  TIMESTAMPTZ,
                          closed_at    TIMESTAMPTZ,
                          CONSTRAINT ck_requests_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'APPROVED',
                                                                          'PARTIALLY_AVAILABLE', 'RESERVED', 'RESERVED_PARTIAL', 'PICKING', 'PICKED', 'PICKING_WITH_SHORTAGE',
                                                                          'READY_FOR_ISSUE', 'ISSUED', 'REJECTED', 'CANCELLED', 'CLOSED')),
                          CONSTRAINT ck_requests_priority CHECK (priority BETWEEN 0 AND 9)
);
CREATE INDEX idx_requests_requester ON requests (requester_id, created_at DESC);
CREATE INDEX idx_requests_status ON requests (status);

CREATE TABLE request_items (
                               id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               request_id          UUID           NOT NULL REFERENCES requests (id),
                               line_no             INT            NOT NULL,
                               product_id          UUID           NOT NULL,
                               product_sku         VARCHAR(100)   NOT NULL,        -- снимок номенклатуры на момент создания
                               product_name        VARCHAR(255)   NOT NULL,
                               unit_code           VARCHAR(20)    NOT NULL,
                               unit_allow_fractions BOOLEAN       NOT NULL DEFAULT TRUE,
                               requested_quantity  NUMERIC(18, 4) NOT NULL,
                               approved_quantity   NUMERIC(18, 4),                 -- решение кладовщика (после проверки наличия)
                               reserved_quantity   NUMERIC(18, 4) NOT NULL DEFAULT 0,
                               issued_quantity     NUMERIC(18, 4) NOT NULL DEFAULT 0,
                               comment             TEXT,
                               CONSTRAINT uq_request_items_line UNIQUE (request_id, line_no),
                               CONSTRAINT ck_request_items_requested CHECK (requested_quantity > 0),
                               CONSTRAINT ck_request_items_approved CHECK (approved_quantity IS NULL
                                   OR (approved_quantity >= 0 AND approved_quantity <= requested_quantity)),
                               CONSTRAINT ck_request_items_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= requested_quantity),
                               CONSTRAINT ck_request_items_issued CHECK (issued_quantity >= 0 AND issued_quantity <= reserved_quantity)
);

-- История статусов: кто и когда менял (разд. 5.4 ТЗ); записи неизменяемы
CREATE TABLE request_status_history (
                                        id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                        request_id  UUID        NOT NULL REFERENCES requests (id),
                                        from_status VARCHAR(40) NOT NULL,
                                        to_status   VARCHAR(40) NOT NULL,
                                        actor_id    UUID        NOT NULL,
                                        comment     TEXT,
                                        created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_request_history_request ON request_status_history (request_id, created_at);

CREATE FUNCTION forbid_history_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'request_status_history: записи истории не изменяются и не удаляются';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_request_history_immutable
    BEFORE UPDATE OR DELETE ON request_status_history
    FOR EACH ROW EXECUTE FUNCTION forbid_history_change();