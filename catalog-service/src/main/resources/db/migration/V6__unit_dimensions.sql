-- M5: тип величины единиц измерения и атрибутов. Единица CM поддерживается конвертером размещения, но не была в каталоге.
ALTER TABLE units ADD COLUMN dimension VARCHAR(20) NOT NULL DEFAULT 'OTHER';
ALTER TABLE units ADD CONSTRAINT ck_units_dimension CHECK (dimension IN ('LENGTH', 'MASS', 'COUNT', 'OTHER'));
UPDATE units SET dimension = 'LENGTH' WHERE code IN ('MM', 'M');
UPDATE units SET dimension = 'MASS'   WHERE code = 'KG';
UPDATE units SET dimension = 'COUNT'  WHERE code = 'PCS';
INSERT INTO units (code, name, allow_fractions, dimension) VALUES ('CM', 'Сантиметр', TRUE, 'LENGTH')
ON CONFLICT (code) DO NOTHING;

ALTER TABLE attribute_definitions ADD COLUMN dimension VARCHAR(20);
ALTER TABLE attribute_definitions ADD CONSTRAINT ck_attr_def_dimension
    CHECK (dimension IS NULL OR dimension IN ('LENGTH', 'MASS'));
UPDATE attribute_definitions SET dimension = 'LENGTH' WHERE code IN ('LENGTH', 'WIDTH', 'HEIGHT', 'DIAMETER');
UPDATE attribute_definitions SET dimension = 'MASS'   WHERE code = 'MASS';
