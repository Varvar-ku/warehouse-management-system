# P1-10 и R5: служебная идентичность и внутренний API inventory

Сервисы: common-lib, inventory-service, request-service. Миграций нет. Порядок выкладки: common-lib (пересборка всех),
inventory-service, request-service (если выложить inventory раньше, request ещё шлёт токен пользователя и получит 403,
поэтому выкладывайте **вместе**).

## Как работает
1. `ServiceTokenIssuer` (common-lib, бин в `JwtSecurityConfiguration`): выпускает JWT на 60 секунд, подписанный тем же
   секретом. Claims: `sub` = пользователь, от имени которого действие (аудит и журналы сохраняют настоящего исполнителя),
   `roles` = `["SERVICE"]`, `svc` = имя вызывающего сервиса, `jti`.
2. inventory-service: `@RequestServiceOnly` (`hasRole('SERVICE')` и `svc == request-service`) стоит на
   `POST /reservations`, `POST /reservations/release`, `POST /issues`, `POST /issues/{id}/confirm`, `POST /issues/{id}/cancel`.
   Кладовщик и администратор получают на них 403. Чтение (`availability`, списки резервов и документов) открыто
   кладовщику и службе (`STOREKEEPER`, `SERVICE`).
3. request-service: `InventoryClient` читает с токеном пользователя, а изменяет (резерв, снятие, документы выдачи)
   со служебным токеном через отдельный `RestClient` (`ServiceClientConfig`, бин `inventoryServiceRestClient`).
   Побочный выигрыш: отмена заявки самим пользователем после резерва больше не падает на роли STOREKEEPER.

## Файлы
- common-lib: `security/ServiceTokenIssuer` (новый), `security/JwtSecurityConfiguration` (бин).
- inventory-service: `security/RequestServiceOnly` (новый), `controller/ReservationController`, `controller/IssueController`.
- request-service: `client/ServiceClientConfig` (новый), `client/InventoryClient`, тест `client/ServiceTokenIssuerTest`.

## Проверить
- URL inventory для нового клиента: `@Value("${app.services.inventory-url:http://localhost:8084}")`. Если в
  `application.yaml` request-service ключ называется иначе, поправьте строку в `ServiceClientConfig`.
- **auth-service не должен позволять назначать роль SERVICE пользователю** (проверьте валидацию ролей в
  `UserService`/`GET /roles`: допустимы только USER, STOREKEEPER, WAREHOUSE_WORKER, ADMIN).
- Сценарии, которые вызывают `POST /reservations`, `/release`, `POST /issues` напрямую под кладовщиком
  (`scenario-4b` и подобные), теперь получат 403: это ожидаемо, выдача и резерв идут только через заявку.
  Ручного способа снять «осиротевший» резерв больше нет: нужна сверка резервов (B5).
- Gateway не менялся: маршруты `/reservations/**` и `/issues/**` остаются для чтения; запись закрыта на сервисе.
- Тесты: `ServiceTokenIssuerTest` (подпись, роль, `svc`, срок, чужой ключ). Если в `common-lib` и request-service
  нет `nimbus-jose-jwt` в compile-зависимостях, он приходит с `spring-boot-starter-security-oauth2-resource-server`.

## Ограничения (остаются до P2-11)
- Секрет HS256 общий, поэтому любой сервис технически может выпустить такой токен. Защита здесь от людей и от
  случайных вызовов, а не от скомпрометированного сервиса. Решение: RS256/JWKS и `aud` (пункт 11 плана безопасности).
- Служебный токен принимается и на пользовательских эндпоинтах, где разрешена роль SERVICE (чтение резервов и документов).
