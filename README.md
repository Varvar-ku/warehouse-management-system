# WMS: система управления складом

Микросервисы на Java 21 / Spring Boot 4.1.1 (Maven multi-module), PostgreSQL 18, Kafka (KRaft), фронтенд на React (папка `frontend/`).

## Порты

| Сервис | Порт | БД |
|---|---|---|
| api-gateway | 8080 | нет |
| auth-service | 8081 | auth_db |
| catalog-service | 8082 | catalog_db |
| warehouse-service | 8083 | warehouse_db |
| inventory-service | 8084 | inventory_db |
| request-service | 8085 | request_db |
| audit-service | 8086 | audit_db |
| notification-service | 8087 | notification_db |
| report-service | 8088 | report_db |
| PostgreSQL (Docker) | 127.0.0.1:5433 | |
| Kafka (Docker) | 127.0.0.1:9092 | |

Все сервисы по умолчанию слушают только `127.0.0.1` (переменная `SERVER_ADDRESS`).

## Переменные окружения

Значения в репозиторий не кладутся.

| Переменная | Где нужна | Назначение |
|---|---|---|
| `JWT_PRIVATE_KEY_FILE` | auth-service | файл приватного ключа подписи JWT (RS256, PKCS#8 PEM), хранится **вне репозитория** |
| `REQUEST_SERVICE_KEY_FILE` | request-service | файл приватного ключа, которым request-service подписывает служебные токены для inventory-service |
| `AUTH_JWKS_URI` | сервисы и gateway | необязательно: адрес публичных ключей (по умолчанию `<AUTH_SERVICE_URL>/.well-known/jwks.json`) |
| `POSTGRES_PASSWORD` | docker compose | пароль суперпользователя `warehouse` (только администрирование, сервисы под ним не работают) |
| `<SVC>_DB_USER`, `<SVC>_DB_PASSWORD` | сервис `<svc>` | runtime-роль `<svc>_app` (чтение и запись данных, без DDL) |
| `<SVC>_MIGRATION_USER`, `<SVC>_MIGRATION_PASSWORD` | сервис `<svc>` | владелец базы `<svc>_owner`, под ним Flyway выполняет миграции |
| `BOOTSTRAP_ADMIN_PASSWORD` | auth-service | пароль первого администратора (создаётся, только если таблица пользователей пуста) |
| `SERVER_ADDRESS` | все сервисы | адрес прослушивания, по умолчанию `127.0.0.1` |
| `CORS_ALLOWED_ORIGINS` | gateway | адреса фронтенда |
| `WMS_ADMIN_PASSWORD` | скрипты `scripts/*.ps1` | пароль admin для сценариев |

`<SVC>` это AUTH, CATALOG, WAREHOUSE, INVENTORY, REQUEST, NOTIFICATION, AUDIT, REPORT (например, `INVENTORY_DB_PASSWORD`). У каждого сервиса свои две роли и право подключения только к своей базе `<svc>_db`. Приложение не может менять схему и отключать триггеры; права runtime-роли выдаёт Flyway-callback `db/migration/afterMigrate__runtime_privileges.sql` сервиса.

Токены подписываются парой RSA-ключей (RS256). Приватный ключ есть только у auth-service, остальные сервисы и gateway проверяют токен публичным ключом из `GET /.well-known/jwks.json` auth-service (через gateway этот путь не проброшен). Служебные токены (request-service -> inventory-service) подписаны отдельным ключом request-service, inventory принимает только его. В токене проверяются издатель, audience `warehouse-api` и срок.

Профиль `dev` (`SPRING_PROFILES_ACTIVE=dev`) создаёт файл ключа при первом запуске, если его нет. Вне профиля `dev` отсутствующий ключ останавливает сервис. Ключ нужно создать заранее: `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem` (формат PKCS#8). Файл ключа не копируйте в репозиторий и не отдавайте сервисам, которым он не нужен.

**Смена ключа.** Новый ключ кладётся в файл с другим именем, `JWT_PRIVATE_KEY_FILE` переключается на него, а публичная часть прежнего ключа (`openssl pkey -in old.pem -pubout -out old-public.pem`) указывается в `app.security.jwt.previous-public-key-file`. Токены, выданные прежним ключом, продолжают приниматься до истечения (15 минут), затем это поле можно убрать. Сервисы подхватывают новый kid автоматически (JWKS кэшируется 5 минут).

## Первый запуск (PowerShell, из корня репозитория)

```powershell
.\scripts\generate-secrets.ps1          # создаёт infrastructure\.env со случайными секретами (включая роли БД сервисов)
cd infrastructure; docker compose up -d; cd ..
. .\scripts\dev-env.ps1 -IdeaLine       # загружает переменные в сессию и печатает строку для IntelliJ
.\mvnw.cmd clean package
```

Запуск сервисов (профиль `dev`, переменные из строки выше в Run Configuration): Docker (`healthy`), auth, catalog, warehouse, inventory, request, audit, notification, report, gateway.

Скрипты проверки: `. .\scripts\dev-env.ps1`, затем `. .\scripts\api.ps1` и нужный `scenario-*.ps1`.

## Если том PostgreSQL уже существует

`POSTGRES_PASSWORD` и роли сервисов создаются только на новом томе. Для существующего:

```powershell
.\scripts\rotate-db-password.ps1        # пароль суперпользователя warehouse из infrastructure\.env
.\scripts\generate-db-passwords.ps1     # если в .env ещё нет переменных <SVC>_DB_* и <SVC>_MIGRATION_*
.\scripts\apply-db-roles.ps1            # создаёт роли <svc>_owner и <svc>_app, передаёт им владение, меняет пароли
```

После этого перезапустите сервисы. `apply-db-roles.ps1` можно запускать повторно: так меняются пароли ролей.

