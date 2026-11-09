-- S2: аренда операции на заявке. Пока выполняется внешний вызов (inventory, auth), заявка не блокируется в БД,
-- а помечается токеном операции; другие изменяющие операции по этой заявке получают 409 OPERATION_IN_PROGRESS.
-- Аренда истекает сама (op_until), если процесс упал посреди операции. Колонки не отображаются в сущности Request.
ALTER TABLE requests ADD COLUMN op_token UUID;
ALTER TABLE requests ADD COLUMN op_until TIMESTAMPTZ;
