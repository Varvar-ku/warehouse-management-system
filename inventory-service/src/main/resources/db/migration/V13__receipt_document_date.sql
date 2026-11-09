-- M8: дата накладной (справочное поле, вводит пользователь). Дату приёмки received_at теперь назначает сервер.
ALTER TABLE receipts ADD COLUMN document_date DATE;
-- прежнее значение received_at вводил пользователь: переносим его как дату накладной
UPDATE receipts SET document_date = (received_at AT TIME ZONE 'UTC')::date WHERE document_date IS NULL;
