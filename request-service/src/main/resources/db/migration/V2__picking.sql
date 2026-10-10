-- Ссылка на документ передачи в inventory-service
ALTER TABLE requests ADD COLUMN issue_id UUID;
ALTER TABLE requests ADD COLUMN issue_number VARCHAR(50);

CREATE SEQUENCE picking_task_number_seq START 1;

-- Задание на комплектацию. Поля assigned_to из ТЗ нет: исполнители в таблице picking_task_workers (разд. 28 ТЗ)
CREATE TABLE picking_tasks (
                               id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               lock_version BIGINT      NOT NULL DEFAULT 0,
                               number       VARCHAR(50) NOT NULL UNIQUE,
                               request_id   UUID        NOT NULL REFERENCES requests (id),
                               status       VARCHAR(20) NOT NULL DEFAULT 'NEW',
                               created_by   UUID        NOT NULL,
                               created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               completed_at TIMESTAMPTZ,
                               completed_by UUID,
                               CONSTRAINT ck_picking_tasks_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'COMPLETED', 'REJECTED'))
);
-- на заявку не больше одного открытого задания
CREATE UNIQUE INDEX uq_picking_tasks_open ON picking_tasks (request_id) WHERE status IN ('NEW', 'IN_PROGRESS');

CREATE TABLE picking_task_workers (
                                      id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                      task_id      UUID        NOT NULL REFERENCES picking_tasks (id),
                                      worker_id    UUID        NOT NULL,                    -- пользователь с ролью WAREHOUSE_WORKER (auth-service)
                                      role         VARCHAR(20) NOT NULL DEFAULT 'WORKER',
                                      status       VARCHAR(20) NOT NULL DEFAULT 'ASSIGNED',
                                      assigned_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      started_at   TIMESTAMPTZ,
                                      completed_at TIMESTAMPTZ,
                                      comment      TEXT,
                                      CONSTRAINT uq_task_worker UNIQUE (task_id, worker_id),
                                      CONSTRAINT ck_task_workers_role CHECK (role IN ('PRIMARY', 'WORKER')),
                                      CONSTRAINT ck_task_workers_status CHECK (status IN
                                                                               ('ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REASSIGNED'))
);
-- в задании один действующий основной исполнитель
CREATE UNIQUE INDEX uq_task_primary ON picking_task_workers (task_id)
    WHERE role = 'PRIMARY' AND status NOT IN ('CANCELLED', 'REASSIGNED');

CREATE TABLE picking_items (
                               id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                               task_id            UUID           NOT NULL REFERENCES picking_tasks (id),
                               request_item_id    UUID           NOT NULL REFERENCES request_items (id),
                               reservation_id     UUID           NOT NULL,          -- резерв в inventory-service
                               stock_id           UUID           NOT NULL,
                               section_id         UUID           NOT NULL,
                               section_code       VARCHAR(50)    NOT NULL,
                               batch_id           UUID,
                               batch_number       VARCHAR(100),
                               requested_quantity NUMERIC(18, 4) NOT NULL,          -- назначено: после распределения строка может быть разбита на части
                               picked_quantity    NUMERIC(18, 4),
                               status             VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
                               assigned_worker_id UUID,
                               comment            TEXT,                              -- причина недостачи
                               picked_at          TIMESTAMPTZ,
                               CONSTRAINT ck_picking_items_qty CHECK (requested_quantity > 0),
                               CONSTRAINT ck_picking_items_picked CHECK (picked_quantity IS NULL
                                   OR (picked_quantity >= 0 AND picked_quantity <= requested_quantity)),
                               CONSTRAINT ck_picking_items_status CHECK (status IN ('PENDING', 'PICKED', 'SHORTAGE'))
);
CREATE INDEX idx_picking_items_task ON picking_items (task_id, section_code);
CREATE INDEX idx_picking_items_request_item ON picking_items (request_item_id);

-- Журнал переназначений (разд. 28.11 ТЗ)
CREATE TABLE picking_reassignments (
                                       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                       task_id         UUID           NOT NULL REFERENCES picking_tasks (id),
                                       picking_item_id UUID           NOT NULL,
                                       request_item_id UUID           NOT NULL,
                                       from_worker_id  UUID           NOT NULL,
                                       to_worker_id    UUID           NOT NULL,
                                       quantity        NUMERIC(18, 4) NOT NULL,
                                       actor_id        UUID           NOT NULL,
                                       reason          TEXT,
                                       created_at      TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP
);