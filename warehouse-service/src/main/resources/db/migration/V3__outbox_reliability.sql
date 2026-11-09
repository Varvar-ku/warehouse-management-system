-- M1: надёжность очереди outbox.
--  tx_id    : идентификатор транзакции, записавшей событие; relay отправляет только строки завершённых транзакций
--             в порядке (tx_id, id), поэтому событие с меньшим id не обгоняет событие с большим;
--  attempts : число постоянных отказов брокера (слишком большое событие, ошибка сериализации); после лимита
--             событие «паркуется» и не блокирует очередь;
--  last_error: причина последнего постоянного отказа.
ALTER TABLE outbox_event ADD COLUMN attempts   INT          NOT NULL DEFAULT 0;
ALTER TABLE outbox_event ADD COLUMN last_error VARCHAR(500);
ALTER TABLE outbox_event ADD COLUMN tx_id      xid8         NOT NULL DEFAULT pg_current_xact_id();
