-- 1. Признак «допускает дробные количества» (приходит из catalog-service)
ALTER TABLE receipt_items ADD COLUMN unit_allow_fractions BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE batches       ADD COLUMN unit_allow_fractions BOOLEAN NOT NULL DEFAULT TRUE;
-- данные, созданные до введения признака
UPDATE receipt_items SET unit_allow_fractions = FALSE WHERE unit_code = 'PCS';
UPDATE batches       SET unit_allow_fractions = FALSE WHERE unit_code = 'PCS';

CREATE SEQUENCE issue_number_seq START 1;
CREATE SEQUENCE write_off_number_seq START 1;

-- 2. Резервы
CREATE TABLE reservations (
                              id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                              lock_version      BIGINT         NOT NULL DEFAULT 0,
                              request_id        UUID           NOT NULL,              -- заявка и её позиции принадлежат request-service
                              request_item_id   UUID           NOT NULL,
                              stock_id          UUID           NOT NULL REFERENCES stock (id),
                              quantity          NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
                              consumed_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,    -- использовано при выдаче
                              released_quantity NUMERIC(18, 4) NOT NULL DEFAULT 0,    -- освобождено
                              status            VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
                              created_by        UUID           NOT NULL,
                              created_at        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              closed_at         TIMESTAMPTZ,
                              closed_by         UUID,
                              CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED')),
                              CONSTRAINT ck_reservations_amounts CHECK (consumed_quantity >= 0 AND released_quantity >= 0
                                  AND consumed_quantity + released_quantity <= quantity),
    -- закрытый резерв полностью разнесён на «использовано» и «освобождено»
                              CONSTRAINT ck_reservations_closed CHECK (status = 'ACTIVE' OR consumed_quantity + released_quantity = quantity)
);
CREATE INDEX idx_reservations_request ON reservations (request_id, status);
CREATE INDEX idx_reservations_item ON reservations (request_item_id, status);
CREATE INDEX idx_reservations_stock ON reservations (stock_id) WHERE status = 'ACTIVE';

-- 3. Документы передачи
CREATE TABLE issues (
                        id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        lock_version BIGINT      NOT NULL DEFAULT 0,
                        number       VARCHAR(50) NOT NULL UNIQUE,
                        warehouse_id UUID        NOT NULL,
                        request_id   UUID        NOT NULL,
                        issued_to    UUID        NOT NULL,                      -- получатель
                        issued_by    UUID,                                      -- кладовщик, подтвердивший выдачу
                        created_by   UUID        NOT NULL,
                        status       VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                        created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        issued_at    TIMESTAMPTZ,
                        comment      TEXT,
                        cancel_reason TEXT,
                        CONSTRAINT ck_issues_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED'))
);
-- по заявке не бывает двух черновиков: иначе они спорили бы за одни и те же резервы
CREATE UNIQUE INDEX uq_issues_draft_per_request ON issues (request_id) WHERE status = 'DRAFT';

CREATE TABLE issue_items (
                             id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             issue_id        UUID           NOT NULL REFERENCES issues (id),
                             request_item_id UUID           NOT NULL,
                             product_id      UUID           NOT NULL,
                             stock_id        UUID           NOT NULL REFERENCES stock (id),
                             reservation_id  UUID           NOT NULL REFERENCES reservations (id),
                             quantity        NUMERIC(18, 4) NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_issue_items_issue ON issue_items (issue_id);

-- 4. Списания
CREATE TABLE write_offs (
                            id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            lock_version      BIGINT      NOT NULL DEFAULT 0,
                            number            VARCHAR(50) NOT NULL UNIQUE,
                            warehouse_id      UUID        NOT NULL,
                            created_by        UUID        NOT NULL,
                            reason_code       VARCHAR(50) NOT NULL,
                            comment           TEXT,
                            status            VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
                            approval_required BOOLEAN     NOT NULL DEFAULT FALSE,
                            approved_by       UUID,
                            approved_at       TIMESTAMPTZ,
                            confirmed_by      UUID,
                            confirmed_at      TIMESTAMPTZ,
                            cancelled_by      UUID,
                            cancel_reason     TEXT,
                            created_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            CONSTRAINT ck_write_offs_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED'))
);

CREATE TABLE write_off_items (
                                 id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 write_off_id UUID           NOT NULL REFERENCES write_offs (id),
                                 stock_id     UUID           NOT NULL REFERENCES stock (id),
                                 product_id   UUID           NOT NULL,
                                 quantity     NUMERIC(18, 4) NOT NULL CHECK (quantity > 0),
                                 CONSTRAINT uq_write_off_items_stock UNIQUE (write_off_id, stock_id)
);

-- 5. Идемпотентность запросов (п. 15.1 ТЗ)
CREATE TABLE idempotency_keys (
                                  user_id         UUID         NOT NULL,
                                  idempotency_key VARCHAR(100) NOT NULL,
                                  fingerprint     VARCHAR(64)  NOT NULL,                 -- SHA-256 от метода, пути и тела запроса
                                  status          VARCHAR(20)  NOT NULL,                 -- IN_PROGRESS / COMPLETED
                                  response_status INT,
                                  response_body   TEXT,
                                  created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  completed_at    TIMESTAMPTZ,
                                  PRIMARY KEY (user_id, idempotency_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_keys (created_at);