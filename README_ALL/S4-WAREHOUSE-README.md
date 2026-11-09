# S4 (часть 1): аудит warehouse-service через outbox

Сервис: warehouse-service. Миграций нет: таблица `outbox_event` (V2) и `wms.outbox.enabled: true` уже есть.

## Что изменено
- `WarehouseAudit` (новый): запись события аудита через `OutboxPublisher` в той же транзакции; исполнитель берётся из JWT.
- `WarehouseService`: `warehouse.created`, `warehouse.updated` (с прежним и новым признаком активности).
- `SectionService`: `section.created`, `section.updated`, `section.status_changed` (из какого статуса в какой);
  прежнее событие `section-status` для inventory осталось.
- `PlacementRuleService`: `placement_rule.created`, `placement_rule.updated` (с версией правила).
- Строки лога `AUDIT:` в этих сервисах убраны: журнал теперь единственный источник.

## Проверить
- audit-service принимает любые `action` (в схеме события `AuditEvent` ничего не менялось).
- Сценарий: создать склад, секцию, правило, заблокировать секцию: в `GET /api/v1/audit/events` под ADMIN 5 новых записей.
- Если `wms.outbox.enabled=false`, в лог пишется ошибка и событие теряется (как и для статуса секции).

## Дальше по S4
auth-service и catalog-service ещё не пишут в аудит (нужны их pom, application.yaml и список миграций),
обработка «мёртвых» сообщений (DLT) в audit, report и notification (нужны их потребители Kafka).
