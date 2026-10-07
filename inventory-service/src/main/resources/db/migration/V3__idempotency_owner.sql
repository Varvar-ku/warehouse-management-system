-- Владелец записи: только он может завершить или освободить ключ (защита от перехвата и чужого удаления)
ALTER TABLE idempotency_keys ADD COLUMN owner UUID;