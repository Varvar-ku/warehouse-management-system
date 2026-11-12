-- P1-9: права runtime-роли audit_app.
-- Callback выполняется Flyway (под владельцем БД audit_owner) после каждого migrate,
-- поэтому новые таблицы и последовательности получают права автоматически.
-- Приложение может читать и менять данные, но не может менять схему и отключать триггеры.
-- Если роли нет (например, тесты на Testcontainers под суперпользователем), скрипт ничего не делает.
DO $$
DECLARE
    app_role        CONSTANT text   := 'audit_app';
    immutable_tables CONSTANT text[] := ARRAY['audit_log']::text[];
    t               text;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        RETURN;
    END IF;

    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', app_role);
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', app_role);
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', app_role);

    -- история миграций приложению недоступна
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        EXECUTE format('REVOKE ALL ON TABLE public.flyway_schema_history FROM %I', app_role);
    END IF;

    -- неизменяемые журналы: только добавление и чтение, даже если триггер защиты будет отключён
    FOREACH t IN ARRAY immutable_tables LOOP
        IF to_regclass('public.' || t) IS NOT NULL THEN
            EXECUTE format('REVOKE UPDATE, DELETE ON TABLE public.%I FROM %I', t, app_role);
        END IF;
    END LOOP;
END
$$;
