ALTER TABLE units ADD COLUMN allow_fractions BOOLEAN NOT NULL DEFAULT TRUE;
-- штуки не бывают дробными
UPDATE units SET allow_fractions = FALSE WHERE code = 'PCS';