# Смена секретов и проверка

Команды выполняются в PowerShell из корня репозитория. Сервисы в IntelliJ перед сменой нужно остановить.

## 1. Порядок смены секретов

1. Сгенерировать новые значения (меняет пароль БД, `JWT_SECRET`, пароль админа):
   ```powershell
   .\scripts\generate-secrets.ps1 -Force
   ```
2. Поставить новый пароль пользователю `warehouse` в PostgreSQL (контейнер должен быть `healthy`):
   ```powershell
   .\scripts\rotate-db-password.ps1
   ```
3. Поставить новый пароль администратору в `auth_db`. Пароль админа в БД сам не меняется: администратор создаётся только при пустой таблице `users`.
   ```powershell
   . .\scripts\dev-env.ps1
   docker exec warehouse-postgres psql -U warehouse -d postgres -c "CREATE EXTENSION IF NOT EXISTS pgcrypto" | Out-Null
   $pw   = $env:WMS_ADMIN_PASSWORD
   $hash = (docker exec warehouse-postgres psql -U warehouse -d postgres -t -A -c "SELECT crypt('$pw', gen_salt('bf', 10))").Trim()
   docker exec warehouse-postgres psql -U warehouse -d postgres -c "DROP EXTENSION pgcrypto" | Out-Null
   "UPDATE users SET password_hash = '$hash' WHERE lower(login) = 'admin';" | docker exec -i warehouse-postgres psql -U warehouse -d auth_db
   ```
   Ожидается `UPDATE 1`.
4. Получить строку переменных и вставить её в **Environment variables** каждой Run Configuration (девять сервисов, включая gateway):
   ```powershell
   . .\scripts\dev-env.ps1 -IdeaLine
   ```
   Поле включается через **Modify options → Environment variables**. Профиль `dev` остаётся.
5. Запустить: auth, catalog, warehouse, inventory, request, audit, notification, report, затем gateway.

Старые токены после смены `JWT_SECRET` недействительны, пользователи входят заново.

Если меняется только пароль БД, достаточно шагов 1–2 (без `-Force` файл не перезапишется, поэтому отредактируйте `POSTGRES_PASSWORD` и `DB_PASSWORD` в `infrastructure\.env` одинаково), затем шаги 4–5.

## 2. Проверка после смены

Пароль БД совпадает в БД и в `.env` (должна вернуться таблица с `1`):
```powershell
. .\scripts\dev-env.ps1
$ip = docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' warehouse-postgres
docker exec -e PGPASSWORD=$env:DB_PASSWORD warehouse-postgres psql -h $ip -U warehouse -d postgres -c "select 1"
```

Порты только на `127.0.0.1`:
```powershell
docker port warehouse-postgres
netstat -ano | findstr "5433 9092 8080 8081 8082 8083 8084 8085 8086 8087 8088" | findstr LISTENING
```
В адресах не должно быть `0.0.0.0` и `[::]`.

Вход и роли:
```powershell
. .\scripts\api.ps1; . .\scripts\dev-env.ps1
$ha = Login "admin" $env:WMS_ADMIN_PASSWORD     # без ошибки
$hu = Login "user1" "User-12345"
TryGet "http://127.0.0.1:8081/actuator/metrics" $hu   # HTTP 403
TryGet "http://127.0.0.1:8081/actuator/metrics" $ha   # список метрик
TryGet "http://127.0.0.1:8081/actuator/health" @{}    # status UP без токена
```

Регрессия: `.\scripts\scenario-5a.ps1` и `.\scripts\scenario-5b.ps1` завершаются `ALL CHECKS PASSED`.

## 3. Типичные ошибки

| Симптом | Причина | Что сделать |
|---|---|---|
| `28P01 password authentication failed` при старте сервиса | В Run Configuration нет `DB_PASSWORD` или он устарел, либо пароль в БД не равен `.env` | Шаг 2, затем шаг 4 |
| 401 при `Login "admin" ...` | Пароль админа в `auth_db` не равен `WMS_ADMIN_PASSWORD` | Шаг 3 |
| Сервис не стартует: секрет короче 32 байт или публичный dev-секрет | `JWT_SECRET` не задан, а профиль `dev` не включён | Шаг 4, проверить Active profiles |
| `POSTGRES_PASSWORD is missing a value` при `docker compose` | Нет `infrastructure\.env` | `.\scripts\generate-secrets.ps1` |
| `Connection refused` между сервисами | `localhost` резолвится в IPv6 | Заменить `localhost` на `127.0.0.1` в URL по умолчанию |

## 4. Правила

- `infrastructure\.env` не попадает в git (есть в `.gitignore`). Значения секретов не пересылаются в чатах и не вставляются в README.
- Тестовые пользователи (`store1`, `user1`, `worker1`, `worker2`, `user2`) с dev-паролями допустимы только на локальной машине. Перед любым общим стендом их нужно удалить.
