-- B4: новый статус позиции приёмки REJECTED (недостача или брак, закрыта с нулевым приходом).
-- Имя CHECK-ограничения на placement_status неизвестно заранее, поэтому находим и снимаем его динамически.
DO $$
DECLARE c record;
BEGIN
    FOR c IN
        SELECT con.conname
        FROM pg_constraint con
        WHERE con.conrelid = 'receipt_items'::regclass
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) ILIKE '%placement_status%'
    LOOP
        EXECUTE format('ALTER TABLE receipt_items DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE receipt_items
    ADD CONSTRAINT ck_receipt_items_placement_status
    CHECK (placement_status IN ('NEW', 'PENDING', 'DISCREPANCY', 'PLACED', 'REJECTED'));
