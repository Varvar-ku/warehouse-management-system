-- Отчёт «Заявки»: по одной строке на заявку, обновляется при каждой смене статуса (последний снимок побеждает).
CREATE TABLE request_facts (
    request_id         UUID          PRIMARY KEY,
    number             VARCHAR(50)   NOT NULL,
    requester_id       UUID,
    warehouse_id       UUID,
    status             VARCHAR(30)   NOT NULL,
    items_count        INTEGER,
    requested_quantity NUMERIC(18,4),
    approved_quantity  NUMERIC(18,4),
    reserved_quantity  NUMERIC(18,4),
    issued_quantity    NUMERIC(18,4),
    created_at         TIMESTAMPTZ   NOT NULL,
    closed_at          TIMESTAMPTZ,
    issue_number       VARCHAR(50),
    last_event_at      TIMESTAMPTZ   NOT NULL,
    last_event_id      UUID          NOT NULL
);

CREATE INDEX idx_request_facts_created   ON request_facts (created_at DESC);
CREATE INDEX idx_request_facts_status    ON request_facts (status, created_at DESC);
CREATE INDEX idx_request_facts_requester ON request_facts (requester_id, created_at DESC);

-- Отчёт «Расхождения»: недовыдача по заявке (REQUEST) и расхождение при размещении поступления (RECEIPT).
CREATE TABLE discrepancy_facts (
    id                 UUID          PRIMARY KEY,         -- eventId аудит-события
    occurred_at        TIMESTAMPTZ   NOT NULL,
    source             VARCHAR(10)   NOT NULL,            -- REQUEST | RECEIPT
    document_id        UUID,
    document_number    VARCHAR(50),
    line_no            INTEGER,
    product_sku        VARCHAR(100),
    product_name       VARCHAR(255),
    unit_code          VARCHAR(20),
    required_quantity  NUMERIC(18,4),
    actual_quantity    NUMERIC(18,4),
    deviation          NUMERIC(18,4) GENERATED ALWAYS AS (actual_quantity - required_quantity) STORED,
    reason             TEXT,
    responsible_id     UUID,
    planned_section    VARCHAR(50),
    actual_section     VARCHAR(50),
    issue_number       VARCHAR(50),
    resolution         VARCHAR(30),
    resolution_comment TEXT,
    resolved_at        TIMESTAMPTZ,
    resolved_by        UUID,
    CONSTRAINT ck_discrepancy_source CHECK (source IN ('REQUEST', 'RECEIPT'))
);

CREATE INDEX idx_discrepancy_time     ON discrepancy_facts (occurred_at DESC);
CREATE INDEX idx_discrepancy_source   ON discrepancy_facts (source, occurred_at DESC);
CREATE INDEX idx_discrepancy_document ON discrepancy_facts (source, document_id, line_no);
