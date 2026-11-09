-- Секция заблокирована или в архиве (снимок из warehouse-service, обновляется событием section-status).
-- Из такой секции резервировать нельзя; физический остаток при этом остаётся.
ALTER TABLE stock ADD COLUMN section_blocked BOOLEAN NOT NULL DEFAULT FALSE;
