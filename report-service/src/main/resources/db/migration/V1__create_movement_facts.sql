-- Модель чтения: по одной строке на каждое движение остатка (снимок из события inventory-service).
CREATE TABLE movement_facts (
    id              UUID          PRIMARY KEY,           -- id движения в inventory-service
    movement_type   VARCHAR(30)   NOT NULL,
    warehouse_id    UUID          NOT NULL,
    product_id      UUID          NOT NULL,
    product_sku     VARCHAR(100),
    product_name    VARCHAR(255),
    unit_code       VARCHAR(20)   NOT NULL,
    batch_id        UUID,
    batch_number    VARCHAR(100),
    expiry_at       TIMESTAMPTZ,
    from_section_id UUID,
    from_section_code VARCHAR(50),
    to_section_id   UUID,
    to_section_code VARCHAR(50),
    quantity        NUMERIC(18,4) NOT NULL,
    document_type   VARCHAR(50)   NOT NULL,
    document_id     UUID          NOT NULL,
    document_number VARCHAR(50),
    performed_by    UUID          NOT NULL,
    performed_at    TIMESTAMPTZ   NOT NULL,
    comment         TEXT,
    trace_id        VARCHAR(64),
    context         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    received_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_movement_facts_performed  ON movement_facts (performed_at DESC);
CREATE INDEX idx_movement_facts_type_time  ON movement_facts (movement_type, performed_at DESC);
CREATE INDEX idx_movement_facts_product    ON movement_facts (product_id, performed_at DESC);
CREATE INDEX idx_movement_facts_document   ON movement_facts (document_id);
