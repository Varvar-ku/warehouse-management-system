-- Индексы под отчёт «Остатки» (GET /api/v1/reports/stock).
CREATE INDEX IF NOT EXISTS idx_movement_facts_to_section
    ON movement_facts (warehouse_id, to_section_id, performed_at) WHERE to_section_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_movement_facts_from_section
    ON movement_facts (warehouse_id, from_section_id, performed_at) WHERE from_section_id IS NOT NULL;
