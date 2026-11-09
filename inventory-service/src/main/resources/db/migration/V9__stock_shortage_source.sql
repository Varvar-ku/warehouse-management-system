-- B1 (часть 2): недостача может возникнуть и при снятии резерва (отмена заявки с недобором), без документа выдачи
ALTER TABLE stock_shortages ALTER COLUMN issue_id DROP NOT NULL;
ALTER TABLE stock_shortages ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ISSUE';
ALTER TABLE stock_shortages ADD CONSTRAINT ck_stock_shortages_source CHECK (source IN ('ISSUE', 'CANCELLATION'));
ALTER TABLE stock_shortages ADD CONSTRAINT ck_stock_shortages_issue CHECK (source <> 'ISSUE' OR issue_id IS NOT NULL);
