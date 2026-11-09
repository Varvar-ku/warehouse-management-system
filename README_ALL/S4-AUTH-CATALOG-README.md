# S4 (часть 2): аудит в auth-service и catalog-service через outbox

## Подключение outbox (по образцу warehouse-service)
Для каждого сервиса: зависимость `spring-boot-starter-kafka` в `pom.xml`; в `application.yaml` блок `spring.kafka`
(адрес брокера `KAFKA_BOOTSTRAP`, надёжные настройки продюсера как в inventory) и `wms.outbox.enabled: true`;
миграция `V4__outbox_event.sql` с таблицей `outbox_event` (последние миграции: auth V3, catalog V3; если ваши номера
другие, переименуйте файл до первого запуска). Полные `pom.xml` и `application.yaml` обоих сервисов в архиве.

## auth-service
`AuthAudit` (новый), правки `AuthService` и `UserService`. События:
- `auth.login_succeeded` (пользователь, адрес клиента);
- `auth.login_failed` (причина `UNKNOWN_LOGIN`, `BAD_PASSWORD`, `ACCOUNT_DISABLED`, адрес клиента). Сам логин не пишется
  (в поле входа иногда вводят пароль): для существующей учётной записи пишется её id, для неизвестного логина только адрес.
  Заблокированные попытки (LOGIN_THROTTLED) не пишутся, иначе перебор заполнял бы журнал. Запись сохраняется, несмотря
  на 401, благодаря `noRollbackFor`;
- `user.created`, `user.updated` (изменённые поля без значений, активность и роли до и после), `user.password_reset`,
  `user.password_changed`, `user.bootstrap_admin_created`. Пароли и хэши в события не попадают.

## catalog-service
`CatalogAudit` (новый), правки `ProductService` и `ReferenceService`; исполнитель берётся из JWT:
`product.created`, `product.updated` (список изменённых полей), `product.archived`, `product.restored`,
`product_type.created`, `product_type.updated`, `unit.created`, `attribute_definition.created`.

## Проверить
- Роль `SERVICE` (служебные токены) не может быть назначена пользователю: `UserService.resolveRoles` принимает только
  роли из таблицы `roles` (USER, STOREKEEPER, WAREHOUSE_WORKER, ADMIN). Не добавляйте `SERVICE` в эту таблицу.
- `KAFKA_BOOTSTRAP` должен быть задан и для auth, и для catalog; без брокера outbox копится в таблице, а сервис работает.
- Сценарий: войти неверным паролем и верным, создать пользователя, сменить роль, создать и архивировать товар:
  в `GET /api/v1/audit/events` под ADMIN появляются соответствующие записи.
- Обратите внимание: `ClientIp` берёт последний адрес `X-Forwarded-For`; за nginx все входы получат один адрес (M6).
