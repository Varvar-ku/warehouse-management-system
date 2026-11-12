#!/bin/sh
# P1-9: отдельные роли БД для каждого сервиса.
#
#   <svc>_owner  владелец базы <svc>_db: выполняет миграции Flyway, может менять схему и триггеры.
#   <svc>_app    runtime-роль сервиса: SELECT/INSERT/UPDATE/DELETE, без DDL. Права выдаёт
#                Flyway-callback afterMigrate__runtime_privileges.sql (в каждом сервисе).
#
# Суперпользователь (POSTGRES_USER) остаётся только для администрирования, сервисы под ним не работают.
# Скрипт идемпотентный: выполняется при первом создании тома (docker-entrypoint-initdb.d), а для уже
# существующего тома запускается через scripts/apply-db-roles.ps1. Повторный запуск меняет пароли
# ролей на значения из окружения (так выполняется смена паролей).
set -e

PSQL="psql -v ON_ERROR_STOP=1 -U ${POSTGRES_USER:-warehouse}"
SERVICES="auth catalog warehouse inventory request notification audit report"

for svc in $SERVICES; do
  up=$(echo "$svc" | tr 'a-z' 'A-Z')
  eval "app_pw=\${${up}_DB_PASSWORD:-}"
  eval "owner_pw=\${${up}_MIGRATION_PASSWORD:-}"
  if [ -z "$app_pw" ] || [ -z "$owner_pw" ]; then
    echo "02-roles: not set ${up}_DB_PASSWORD or ${up}_MIGRATION_PASSWORD (see infrastructure/.env.example)" >&2
    false
  fi

  echo "02-roles: ${svc}_db"

  # 1. роли, база, право подключения
  $PSQL -d postgres \
    -v db="${svc}_db" -v owner="${svc}_owner" -v app="${svc}_app" \
    -v owner_pw="$owner_pw" -v app_pw="$app_pw" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'owner') \gexec
SELECT format('CREATE ROLE %I LOGIN', :'app')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'owner', :'owner_pw') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE', :'app', :'app_pw') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
ALTER DATABASE :"db" OWNER TO :"owner";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO :"app";
SQL

  # 2. схема public и передача владения уже существующими объектами (для томов, созданных до P1-9)
  $PSQL -d "${svc}_db" -v owner="${svc}_owner" -v app="${svc}_app" <<'SQL'
SET wms.owner = :'owner';
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE, CREATE ON SCHEMA public TO :"owner";
GRANT USAGE ON SCHEMA public TO :"app";
DO $$
DECLARE
    o text := current_setting('wms.owner');
    r record;
BEGIN
    FOR r IN SELECT c.oid::regclass AS obj
               FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p', 'v', 'm')
                AND pg_get_userbyid(c.relowner)::text <> o
    LOOP
        EXECUTE format('ALTER TABLE %s OWNER TO %I', r.obj, o);
    END LOOP;
    -- последовательности таблиц (BIGSERIAL) уже сменили владельца вместе с таблицей, остаются самостоятельные
    FOR r IN SELECT c.oid::regclass AS obj
               FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public' AND c.relkind = 'S'
                AND pg_get_userbyid(c.relowner)::text <> o
    LOOP
        EXECUTE format('ALTER SEQUENCE %s OWNER TO %I', r.obj, o);
    END LOOP;
    FOR r IN SELECT p.oid::regprocedure AS sig
               FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
              WHERE n.nspname = 'public' AND pg_get_userbyid(p.proowner)::text <> o
    LOOP
        EXECUTE format('ALTER ROUTINE %s OWNER TO %I', r.sig, o);
    END LOOP;
END
$$;
SQL
done

echo "02-roles: done"
