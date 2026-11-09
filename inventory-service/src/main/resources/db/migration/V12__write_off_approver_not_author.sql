-- S6: одобряющий списание не может быть его автором (как в stock_adjustments).
-- NOT VALID: ограничение действует для новых и изменяемых строк; старые данные не проверяются и миграцию не ломают.
ALTER TABLE write_offs
    ADD CONSTRAINT ck_write_offs_approver CHECK (approved_by IS NULL OR approved_by <> created_by) NOT VALID;
