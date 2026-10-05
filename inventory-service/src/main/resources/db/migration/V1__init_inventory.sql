-- Номера документов поступления: RC-<год>-<порядковый номер>
CREATE SEQUENCE receipt_number_seq START 1;

CREATE TABLE receipts (
                          id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                          lock_version             BIGINT       NOT NULL DEFAULT 0,
                          number                   VARCHAR(50)  NOT NULL UNIQUE,
                          warehouse_id             UUID         NOT NULL,           -- склад принадлежит warehouse-service, внешнего ключа нет
                          supplier_name            VARCHAR(255) NOT NULL,
                          external_document_number VARCHAR(100),
                          status                   VARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
                          received_at              TIMESTAMPTZ  NOT NULL,
                          created_by               UUID         NOT NULL,
                          created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          updated_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          confirmed_at             TIMESTAMPTZ,
                          confirmed_by             UUID,
                          completed_at             TIMESTAMPTZ,
                          cancel_reason            TEXT,
                          cancelled_by             UUID,
                          CONSTRAINT ck_receipts_status CHECK (status IN ('DRAFT', 'RECEIVING', 'PLACEMENT', 'DONE', 'CANCELLED'))
);
CREATE INDEX idx_receipts_warehouse_status ON receipts (warehouse_id, status);

CREATE TABLE batches (
                         id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         product_id               UUID         NOT NULL,
                         product_sku              VARCHAR(100) NOT NULL,          -- снимок: партия остаётся читаемой, даже если номенклатуру изменят
                         product_name             VARCHAR(255) NOT NULL,
                         unit_code                VARCHAR(20)  NOT NULL,
                         receipt_id               UUID         NOT NULL REFERENCES receipts (id),
                         batch_number             VARCHAR(100) NOT NULL,
                         received_at              TIMESTAMPTZ  NOT NULL,
                         expiry_at                TIMESTAMPTZ,
                         attributes_snapshot_json TEXT         NOT NULL DEFAULT '{}' CHECK (attributes_snapshot_json IS JSON OBJECT),
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_batches_product_number UNIQUE (product_id, batch_number)
);

CREATE TABLE receipt_items (
                               id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               receipt_id                  UUID          NOT NULL REFERENCES receipts (id),
                               line_no                     INT           NOT NULL,
                               product_id                  UUID          NOT NULL,
                               product_sku                 VARCHAR(100)  NOT NULL,
                               product_name                VARCHAR(255)  NOT NULL,
                               product_type_code           VARCHAR(50)   NOT NULL,
                               unit_code                   VARCHAR(20)   NOT NULL,       -- единица номенклатуры (в ТЗ поля нет, а без неё количество не имеет смысла)
                               quantity                    NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
                               batch_number                VARCHAR(100),
                               expiry_at                   TIMESTAMPTZ,
                               characteristics_json        TEXT          NOT NULL DEFAULT '{}' CHECK (characteristics_json IS JSON OBJECT), -- уточнения характеристик, введённые кладовщиком
    snapshot_json               TEXT          CHECK (snapshot_json IS NULL OR snapshot_json IS JSON OBJECT),     -- снимок на момент приёмки, станет attributes_snapshot_json партии
    comment                     TEXT,
    -- рекомендация подбора и её основание (п. 9 ТЗ)
    recommended_section_id      UUID,
    recommended_section_code    VARCHAR(50),
    recommendation_strategy     VARCHAR(30),
    rule_id                     UUID,
    rule_version                INT,
    rule_name                   VARCHAR(255),
    recommendation_calculated_at TIMESTAMPTZ,
    recommendation_notes        TEXT,
    -- решение кладовщика
    target_section_id           UUID,
    target_section_code         VARCHAR(50),
    target_decision             VARCHAR(30),
    -- размещение
    placement_status            VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    placed_section_id           UUID,
    placed_section_code         VARCHAR(50),
    placed_quantity             NUMERIC(18, 4),
    placed_by                   UUID,
    placed_at                   TIMESTAMPTZ,
    discrepancy_comment         TEXT,
    batch_id                    UUID REFERENCES batches (id),
    CONSTRAINT uq_receipt_items_line UNIQUE (receipt_id, line_no),
    CONSTRAINT ck_receipt_items_status CHECK (placement_status IN ('NEW', 'PENDING', 'PLACED', 'DISCREPANCY')),
    CONSTRAINT ck_receipt_items_decision CHECK (target_decision IS NULL
        OR target_decision IN ('RECOMMENDED', 'ALTERNATIVE', 'UNASSIGNED_ZONE'))
);
CREATE INDEX idx_receipt_items_pending_target ON receipt_items (target_section_id) WHERE placement_status = 'PENDING';

-- Задание на размещение (в ТЗ в модели данных отсутствует, но упомянуто в разд. 5.3 и 10): одно на поступление
CREATE TABLE placement_tasks (
                                 id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 lock_version BIGINT      NOT NULL DEFAULT 0,
                                 receipt_id   UUID        NOT NULL UNIQUE REFERENCES receipts (id),
                                 status       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                                 assignee_id  UUID,                                       -- null = задание видят все работники склада
                                 created_by   UUID        NOT NULL,
                                 created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 completed_at TIMESTAMPTZ,
                                 CONSTRAINT ck_placement_tasks_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED'))
);

CREATE TABLE stock (
                       id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       lock_version      BIGINT         NOT NULL DEFAULT 0,
                       warehouse_id      UUID           NOT NULL,
                       product_id        UUID           NOT NULL,
                       section_id        UUID           NOT NULL,
                       section_code      VARCHAR(50)    NOT NULL,                -- код секции неизменяем в warehouse-service, снимок безопасен
                       batch_id          UUID           NOT NULL REFERENCES batches (id),
                       unit_code         VARCHAR(20)    NOT NULL,
                       quantity          NUMERIC(18, 4) NOT NULL DEFAULT 0,
                       reserved_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,
                       updated_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       CONSTRAINT uq_stock UNIQUE (product_id, section_id, batch_id),
    -- разд. 22 ТЗ: остаток не бывает отрицательным; резерв не превышает остаток
                       CONSTRAINT ck_stock_non_negative CHECK (quantity >= 0 AND reserved_quantity >= 0),
                       CONSTRAINT ck_stock_reserved_le_quantity CHECK (reserved_quantity <= quantity)
);
CREATE INDEX idx_stock_warehouse_section ON stock (warehouse_id, section_id);
CREATE INDEX idx_stock_product ON stock (product_id);

CREATE TABLE stock_movements (
                                 id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 movement_type     VARCHAR(30)    NOT NULL,
                                 warehouse_id      UUID           NOT NULL,
                                 product_id        UUID           NOT NULL,
                                 batch_id          UUID REFERENCES batches (id),
                                 from_section_id   UUID,
                                 from_section_code VARCHAR(50),
                                 to_section_id     UUID,
                                 to_section_code   VARCHAR(50),
                                 quantity          NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
                                 unit_code         VARCHAR(20)    NOT NULL,
                                 document_type     VARCHAR(50)    NOT NULL,
                                 document_id       UUID           NOT NULL,
                                 performed_by      UUID           NOT NULL,
                                 performed_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 comment           TEXT,
                                 CONSTRAINT ck_stock_movements_type CHECK (movement_type IN
                                                                           ('RECEIPT', 'ISSUE', 'WRITE_OFF', 'TRANSFER', 'ADJUSTMENT', 'RESERVATION'))
);
CREATE INDEX idx_movements_product ON stock_movements (product_id, performed_at DESC);
CREATE INDEX idx_movements_document ON stock_movements (document_type, document_id);

-- Журнал движений неизменяем: исправления делаются новыми записями (сторно), а не правкой старых
CREATE FUNCTION forbid_movement_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'stock_movements: записи журнала движений не изменяются и не удаляются';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_stock_movements_immutable
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_movement_change();