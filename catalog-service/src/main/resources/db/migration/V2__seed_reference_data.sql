-- Стартовые справочники, без них нельзя создать первую номенклатуру
INSERT INTO units (code, name) VALUES
                                   ('PCS', 'Штука'),
                                   ('KG',  'Килограмм'),
                                   ('M',   'Метр'),
                                   ('MM',  'Миллиметр');

INSERT INTO product_types (code, name) VALUES
                                           ('ARMATURE', 'Арматура'),
                                           ('BOARD',    'Доска'),
                                           ('PIPE',     'Труба'),
                                           ('FASTENER', 'Крепёж');

INSERT INTO attribute_definitions (code, name, data_type) VALUES
                                                              ('LENGTH',   'Длина',    'NUMBER'),
                                                              ('WIDTH',    'Ширина',   'NUMBER'),
                                                              ('HEIGHT',   'Высота',   'NUMBER'),
                                                              ('DIAMETER', 'Диаметр',  'NUMBER'),
                                                              ('MASS',     'Масса',    'NUMBER'),
                                                              ('MATERIAL', 'Материал', 'REFERENCE');

INSERT INTO attribute_options (attribute_id, value)
SELECT id, v FROM attribute_definitions, (VALUES ('METAL'), ('WOOD'), ('PLASTIC')) AS t(v)
WHERE code = 'MATERIAL';