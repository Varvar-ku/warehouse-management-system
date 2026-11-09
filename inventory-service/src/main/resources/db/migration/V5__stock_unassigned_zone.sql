-- Признак "секция - зона Нераспределено". Из такого остатка резервировать нельзя.
-- Секция не меняет зону после создания, поэтому снимок в строке остатка безопасен.
ALTER TABLE stock ADD COLUMN unassigned_zone BOOLEAN NOT NULL DEFAULT FALSE;

-- Выбор кандидатов на резерв ходит только по доступным строкам вне "Нераспределено"
CREATE INDEX idx_stock_reservable ON stock (warehouse_id, product_id)
    WHERE unassigned_zone = FALSE AND quantity > reserved_quantity;
