# WMS frontend

React + TypeScript + Vite + Ant Design + TanStack Query.

## Запуск
1. Поднять бэкенд (Docker, сервисы, gateway на :8080).
2. `npm install`
3. `npm run dev` → http://localhost:5173 (запросы `/api` проксируются на gateway).

## Соглашения
- Все запросы идут через `src/api/client.ts`: токен, `X-Trace-Id`, разбор ошибок `{code, message, details, trace_id}`.
- `idempotencyKey: true` добавляет `Idempotency-Key`. При повторе той же операции передавайте тот же ключ строкой.
- 401 с кодом `UNAUTHORIZED` завершает сессию; неверный пароль приходит с кодом `INVALID_CREDENTIALS`.
- Меню и маршруты по ролям описаны в `src/layout/navigation.ts`. Скрытие пунктов только удобство, права проверяет бэкенд.
- Токен хранится в памяти и `sessionStorage` (не `localStorage`), срок берётся из `exp` токена.

## Разделы и роли
| Раздел | Роли | Где код |
|---|---|---|
| Заявки, уведомления | USER, STOREKEEPER, ADMIN | `features/requests`, `features/notifications` |
| Комплектация, приёмка, выдачи | STOREKEEPER | `features/picking`, `features/receipts`, `features/issues` |
| Мои задания | WAREHOUSE_WORKER | `features/tasks` |
| Остатки | STOREKEEPER, ADMIN | `features/stock` |
| Недостачи, корректировки, списания | STOREKEEPER, ADMIN | `features/shortages`, `features/adjustments`, `features/writeoffs` |
| Отчёты (9 отчётов, выгрузка CSV) | STOREKEEPER, ADMIN | `features/reports` (отчёты описаны в `config.ts`) |
| Пользователи, каталог, склады и секции, правила размещения | ADMIN | `features/admin/*` |
| Журнал аудита | ADMIN | `features/audit` |

Страницы подгружаются через `React.lazy` (маршруты в `src/App.tsx`).

## Соглашения для новых экранов
- Новый отчёт добавляется записью в `src/features/reports/config.ts`: путь API, фильтры и колонки. Отдельная страница не нужна.
- Операции, которые нельзя выполнить дважды (создание, проведение), отправляются с `Idempotency-Key`: `useIdempotencyKey().forBody(body)` выдаёт новый ключ при изменении данных, `reset()` вызывается после успеха.
- Списки бэкенда бывают массивом и страницей `{content}`; аудит отдаёт `{items, totalItems}`. Для приведения есть `api/asArray.ts`.
- Чистая логика (статусы кнопок, сборка условий, CSV) лежит в отдельных `.ts` рядом с компонентами и покрыта тестами.

## Тесты
Vitest ставится отдельно: `npm i -D vitest`. Запуск: `npm test`. Тесты лежат рядом с кодом (`*.test.ts`) и исключены из `tsc`, чтобы сборка не зависела от них. Для тестов форм добавьте `jsdom` и `@testing-library/react` и поставьте `environment: 'jsdom'` в `vitest.config.ts`.

## Сборка и запуск в Docker
```
docker build -t wms-frontend .
docker run -p 8081:80 -e GATEWAY_URL=http://host.docker.internal:8080 wms-frontend
```
nginx отдаёт SPA с запасным маршрутом на `index.html`, проксирует `/api/` на gateway и добавляет заголовки безопасности (CSP, `X-Content-Type-Options`, `Referrer-Policy`). Внимание: `ClientIp` в auth-service берёт ПОСЛЕДНИЙ адрес `X-Forwarded-For`, то есть тот, что дописал gateway. Когда перед gateway стоит nginx, это адрес nginx, и все пользователи попадут в один счётчик защиты входа: 30 неудачных попыток заблокируют вход всем (см. M6 в `wms-backend-analysis.md`). До запуска в таком виде нужно доработать `ClientIp` или настроить доверенный прокси в gateway.

## Работа с клавиатурой
- **Стрелки** ←→↑↓ переводят фокус между кнопками и ссылками по их расположению на экране; **Enter** и пробел нажимают кнопку; **Tab** идёт по порядку. Реализовано одним глобальным обработчиком (`shared/useArrowNav.ts`, геометрия в `shared/arrowGeometry.ts` с тестами). В полях ввода, выпадающих списках, вкладках и меню стрелки остаются их собственными.
- **Быстрый цикл у работника:** фокус сразу попадает в «Принять задание», затем в поле количества первой строки. Ввод → Enter → следующая строка получает фокус.
- **Окна подтверждения:** Enter подтверждает, Esc закрывает. У опасных действий автофокуса на кнопке нет.
- Обводка фокуса задана в `src/focus.css`.
