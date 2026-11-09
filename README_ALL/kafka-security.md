# Kafka: безопасность и эксплуатация (M9)

В разработке брокер слушает только `127.0.0.1:9092` без аутентификации. Для рабочей среды нужно следующее.

## Что уже сделано в коде
- Единое имя переменной адреса брокера: `KAFKA_BOOTSTRAP` (старое `KAFKA_BOOTSTRAP_SERVERS` поддерживается как запасное).
- Тестовый топик `request-events` подключается только профилем `dev` в notification-service.
- notification-service принимает уведомления о заявках только из событий с `service = request-service`.
- audit-service проверяет обязательные поля и версию схемы; дубли отбрасываются по `event_id`.

## Что нужно для рабочей среды (в docker-compose для разработки не реализовано)
1. Аутентификация: `SASL_SSL` (SCRAM-SHA-512 или mTLS), у каждого сервиса свой пользователь (`spring.application.name`).
2. Авторизация (ACL, `StandardAuthorizer`), запрет по умолчанию. Предлагаемая матрица (проверьте группы потребителей):

| Пользователь | Запись (WRITE) | Чтение (READ, группа) |
|---|---|---|
| auth-service, catalog-service, warehouse-service, request-service | `wms.audit.v1` | нет |
| warehouse-service | `warehouse.section-status` | нет |
| inventory-service | `wms.audit.v1`, `wms.stock-movements.v1` | `warehouse.section-status` (группа inventory-service) |
| audit-service | `wms.audit.v1.DLT` | `wms.audit.v1` (группа audit-service) |
| notification-service | `<топик>.DLT` | `wms.audit.v1` (группа notification-service) |
| report-service | `<топик>.DLT` | `wms.audit.v1`, `wms.stock-movements.v1` (группа report-service) |

   Только такая запись гарантирует, что событие о заявке опубликовал request-service, а не любой, кто достучался до брокера.
3. Топики создаются администратором заранее (число разделов, срок хранения), автосоздание отключено.
4. Параметры клиента (один файл профиля для всех сервисов): `spring.kafka.properties.security.protocol=SASL_SSL`,
   `sasl.mechanism=SCRAM-SHA-512`, `sasl.jaas.config` с логином и паролем из переменных окружения.
5. Мониторинг: рост «запаркованных» событий outbox (лог ERROR) и сообщений в `*.DLT`.
