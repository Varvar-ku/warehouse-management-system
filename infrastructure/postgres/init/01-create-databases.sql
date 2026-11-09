-- picking_db удалён: комплектация живёт в request_db (в уже созданном томе лишнюю базу можно удалить: DROP DATABASE picking_db;).
-- Выполняется ОДИН раз при первом создании тома postgres_data.
-- Если тома уже есть: docker compose down -v (данные удалятся!) и запустить заново.
CREATE DATABASE auth_db;
CREATE DATABASE catalog_db;
CREATE DATABASE warehouse_db;
CREATE DATABASE inventory_db;
CREATE DATABASE request_db;
CREATE DATABASE notification_db;
CREATE DATABASE audit_db;
CREATE DATABASE report_db;
