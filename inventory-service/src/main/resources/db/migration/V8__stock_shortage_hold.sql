-- B1. Недостача при сборе не должна возвращаться в оборот как доступный остаток.
-- 1) строка остатка «на проверке»: из резерва, проверки наличия и списания исключается до решения кладовщика
ALTER TABLE stock ADD COLUMN on_hold    BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE stock ADD COLUMN hold_since TIMESTAMPTZ;
CREATE INDEX idx_stock_on_hold ON stock (warehouse_id) WHERE on_hold;

CREATE SEQUENCE shortage_number_seq   START 1;
CREATE SEQUENCE adjustment_number_seq START 1;

-- 2) недостача: одна запись на резерв, закрытый при выдаче с неиспользованным остатком
CREATE TABLE stock_shortages (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lock_version       BIGINT         NOT NULL DEFAULT 0,
    number             VARCHAR(50)    NOT NULL UNIQUE,
    warehouse_id       UUID           NOT NULL,
    product_id         UUID           NOT NULL,
    stock_id           UUID           NOT NULL REFERENCES stock (id),
    reservation_id     UUID           NOT NULL REFERENCES reservations (id),
    request_id         UUID           NOT NULL,
    request_item_id    UUID           NOT NULL,
    issue_id           UUID           NOT NULL REFERENCES issues (id),
    quantity           NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
    unit_code          VARCHAR(20)    NOT NULL,
    status             VARCHAR(20)    NOT NULL DEFAULT 'OPEN',
    resolution         VARCHAR(30),
    resolution_comment TEXT,
    resolved_by        UUID,
    resolved_at        TIMESTAMPTZ,
    adjustment_id      UUID,                                  -- документ корректировки, в который включена недостача
    reported_by        UUID           NOT NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_shortages_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_stock_shortages_resolution CHECK (resolution IS NULL OR resolution IN ('FOUND', 'WRITTEN_OFF')),
    CONSTRAINT ck_stock_shortages_resolved CHECK ((status = 'OPEN' AND resolution IS NULL)
                                               OR (status = 'RESOLVED' AND resolution IS NOT NULL AND resolved_at IS NOT NULL))
);
-- повторная регистрация по тому же резерву невозможна
CREATE UNIQUE INDEX uq_stock_shortages_reservation ON stock_shortages (reservation_id);
CREATE INDEX idx_stock_shortages_open ON stock_shortages (warehouse_id, created_at) WHERE status = 'OPEN';
CREATE INDEX idx_stock_shortages_stock_open ON stock_shortages (stock_id) WHERE status = 'OPEN';

-- 3) документ корректировки (по образцу списания; одобрение обязательно, одобряющий не автор)
CREATE TABLE stock_adjustments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lock_version  BIGINT      NOT NULL DEFAULT 0,
    number        VARCHAR(50) NOT NULL UNIQUE,
    warehouse_id  UUID        NOT NULL,
    created_by    UUID        NOT NULL,
    comment       TEXT        NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    approved_by   UUID,
    approved_at   TIMESTAMPTZ,
    confirmed_by  UUID,
    confirmed_at  TIMESTAMPTZ,
    cancelled_by  UUID,
    cancel_reason TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_stock_adjustments_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_stock_adjustments_approver CHECK (approved_by IS NULL OR approved_by <> created_by)
);

CREATE TABLE stock_adjustment_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    adjustment_id UUID           NOT NULL REFERENCES stock_adjustments (id),
    shortage_id   UUID           NOT NULL REFERENCES stock_shortages (id),
    stock_id      UUID           NOT NULL REFERENCES stock (id),
    product_id    UUID           NOT NULL,
    quantity      NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
    CONSTRAINT uq_adjustment_items_shortage UNIQUE (adjustment_id, shortage_id)
);
CREATE INDEX idx_adjustment_items_adjustment ON stock_adjustment_items (adjustment_id);
