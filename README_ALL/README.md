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
| `JWT_SECRET` | все сервисы и gateway | секрет подписи JWT, не короче 32 байт, одинаковый везде |
| `DB_PASSWORD` | сервисы с БД | пароль пользователя PostgreSQL |
| `DB_USER` | сервисы с БД | пользователь PostgreSQL (по умолчанию `warehouse`) |
| `POSTGRES_PASSWORD` | docker compose | пароль PostgreSQL при создании тома |
| `BOOTSTRAP_ADMIN_PASSWORD` | auth-service | пароль первого администратора (создаётся, только если таблица пользователей пуста) |
| `SERVER_ADDRESS` | все сервисы | адрес прослушивания, по умолчанию `127.0.0.1` |
| `CORS_ALLOWED_ORIGINS` | gateway | адреса фронтенда |
| `WMS_ADMIN_PASSWORD` | скрипты `scripts/*.ps1` | пароль admin для сценариев |

Профиль `dev` (`SPRING_PROFILES_ACTIVE=dev`) подставляет публичные dev-значения, если переменная не задана. Эти значения считаются известными всем; профиль `dev` только для локальной машины.

## Первый запуск (PowerShell, из корня репозитория)

```powershell
.\scripts\generate-secrets.ps1          # создаёт infrastructure\.env со случайными секретами
cd infrastructure; docker compose up -d; cd ..
. .\scripts\dev-env.ps1 -IdeaLine       # загружает переменные в сессию и печатает строку для IntelliJ
.\mvnw.cmd clean package
```

Запуск сервисов (профиль `dev`, переменные из строки выше в Run Configuration): Docker (`healthy`), auth, catalog, warehouse, inventory, request, audit, notification, report, gateway.

Скрипты проверки: `. .\scripts\dev-env.ps1`, затем `. .\scripts\api.ps1` и нужный `scenario-*.ps1`.

## Если том PostgreSQL уже существует

`POSTGRES_PASSWORD` действует только на новый том. Для существующего: `.\scripts\rotate-db-password.ps1`.
