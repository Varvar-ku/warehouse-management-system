-- Журнал операций комплектации: проекция аудит-событий picking.* из request-service.
CREATE TABLE picking_events (
    id          UUID         PRIMARY KEY,            -- eventId аудит-события
    occurred_at TIMESTAMPTZ  NOT NULL,
    action      VARCHAR(60)  NOT NULL,               -- picking.task_created, picking.item_picked ...
    task_id     UUID,
    task_number VARCHAR(30),
    actor_id    UUID,                                -- кто выполнил операцию
    worker_id   UUID,                                -- исполнитель, к которому относится операция
    trace_id    VARCHAR(64),
    details     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    received_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_picking_events_time   ON picking_events (occurred_at DESC);
CREATE INDEX idx_picking_events_action ON picking_events (action, occurred_at DESC);
CREATE INDEX idx_picking_events_worker ON picking_events (worker_id, occurred_at DESC);
CREATE INDEX idx_picking_events_task   ON picking_events (task_id);
