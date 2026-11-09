-- L6: нумерация документов заново каждый год (год по поясу Europe/Moscow, см. app.numbering.zone).
-- Счётчик по паре (префикс, год); значения текущего года продолжают прежние последовательности, чтобы номера не повторялись.
-- Старые последовательности больше не используются (оставлены: применённые миграции не меняем, удалить можно отдельной миграцией).
CREATE TABLE document_counters (
    prefix     VARCHAR(10) NOT NULL,
    year       INT         NOT NULL,
    last_value BIGINT      NOT NULL CHECK (last_value >= 0),
    PRIMARY KEY (prefix, year)
);

INSERT INTO document_counters (prefix, year, last_value)
SELECT 'RQ', y.year, CASE WHEN s.is_called THEN s.last_value ELSE 0 END
FROM request_number_seq s, (SELECT EXTRACT(YEAR FROM (now() AT TIME ZONE 'Europe/Moscow'))::int AS year) y;
INSERT INTO document_counters (prefix, year, last_value)
SELECT 'PT', y.year, CASE WHEN s.is_called THEN s.last_value ELSE 0 END
FROM picking_task_number_seq s, (SELECT EXTRACT(YEAR FROM (now() AT TIME ZONE 'Europe/Moscow'))::int AS year) y;
