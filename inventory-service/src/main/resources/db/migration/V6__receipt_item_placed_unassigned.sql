-- Фактическая секция размещения - "Нераспределено" (снимок из warehouse-service на момент подтверждения).
-- Нужен, чтобы при решении кладовщика ACCEPT_ACTUAL остаток получил верный признак без обращения к соседнему сервису.
ALTER TABLE receipt_items ADD COLUMN placed_unassigned_zone BOOLEAN NOT NULL DEFAULT FALSE;
