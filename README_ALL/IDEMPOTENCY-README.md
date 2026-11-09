# Idempotency-Key в request-service (R12, часть M3)

Тот же механизм, что в inventory-service, перенесён в request-service (пакет `ru.warehouse.request.idempotency`).
Дубль кода временный: в рамках R14 фильтр стоит вынести в common-lib и подключить к обоим сервисам.

## Файлы
- `idempotency/IdempotencyFilter, IdempotencyStore, IdempotencyProperties, IdempotencyConfig`: копия с заменой пакета;
  `IdempotencyConfig` дополнительно регистрирует `IdempotencyProperties` (`@EnableConfigurationProperties`), поэтому
  отдельный `@ConfigurationPropertiesScan` не нужен.
- `db/migration/V4__idempotency_keys.sql`: таблица `idempotency_keys` в request_db (`owner` обязателен).
  Если V4 в request-service уже занят, возьмите следующий номер.
- `test/.../IdempotencyFilterTest`: повтор отдаёт сохранённый ответ, другое тело даёт 422, неуспешный запрос
  освобождает ключ, обязательный путь без ключа даёт 400.

## Поведение
- Для POST, PUT, PATCH и DELETE с заголовком `Idempotency-Key` (8-100 символов): первый запрос выполняется, 2xx-ответ
  сохраняется; повтор с тем же телом отдаёт его с заголовком `Idempotency-Replayed: true`; тот же ключ с другим запросом
  даёт 422; пока первый выполняется, 409. Ключ принадлежит пользователю из JWT.
- По умолчанию заголовок **необязателен** (`requiredPaths` пуст): старые скрипты `api.ps1` продолжают работать.
  Защищены клиенты, которые ключ передают (фронтенд по плану этапа 2). Сделать обязательным можно в `application.yaml`
  request-service, когда скрипты и клиент будут обновлены:

      app:
        idempotency:
          required-paths: [ /api/v1/requests, /api/v1/picking-tasks, /api/v1/requests/*/issue ]
          in-progress-timeout: 2m
          retention: 48h

  (В `required-paths` Ant-шаблоны; `/api/v1/picking-tasks/*/workers` и `/assign-items` добавляйте по мере надобности.)

## Что не меняется и стоит знать
- Гарантия по-прежнему «почти ровно один раз» (M3): бизнес-транзакция и `complete()` ключа в разных транзакциях,
  от дублей после сбоя между ними спасают предметные проверки (уникальные индексы, статусы заявки).
- Отпечаток считается по сырому телу: то же содержимое, сериализованное иначе, даст 422 при повторе.
- Gateway должен пропускать заголовок `Idempotency-Key` (он уже нужен inventory) и CORS его разрешать.

## Проверить
- Классы `PickingService`/контроллеры менять не нужно: фильтр работает на уровне HTTP.
- `scripts`: повторить `POST /requests` с одним ключом: один черновик, второй ответ с `Idempotency-Replayed: true`.
- Зависимости request-service: `spring-boot-starter-jdbc`/JPA (JdbcTemplate), resource server, spring-test (уже есть
  в тестах проекта).
