# Волна 3, пункт 4: разделение обязанностей (S6)

## Решение по умолчанию (вопрос 6 из анализа, подтвердите или измените)
Одному пользователю нельзя назначать роли: **USER + STOREKEEPER** (сам создаёт, проверяет, резервирует и выдаёт свою
заявку) и **STOREKEEPER + ADMIN** (сам создаёт и одобряет списание LOST). Остальные сочетания разрешены. Список задаётся
настройкой (необязательной) в `application.yaml` auth-service:

    app:
      security:
        forbidden-role-combinations: [ "USER+STOREKEEPER", "STOREKEEPER+ADMIN" ]   # пусто: ограничений нет

## Что сделано
- auth-service: `RoleCombinations` (проверка) и `UserService.resolveRoles`: при создании пользователя и при изменении
  набора ролей запрещённое сочетание даёт 409 `ROLE_COMBINATION_FORBIDDEN` с понятным текстом. Изменение других полей у
  уже «нарушающего» пользователя не блокируется. Тесты: `RoleCombinationsTest`.
- inventory-service: `WriteOffService.approve` не даёт одобрить собственное списание (403 `SELF_APPROVAL`, как в
  корректировках). Миграция `V12__write_off_approver_not_author.sql` добавляет то же правило в БД (`NOT VALID`,
  старые строки не проверяются). Файл `UserService` взят из версии «пароли только администратор» (она включает аудит S4).

## ВАЖНО: проверить существующих пользователей
Правило не снимает роли у уже созданных учётных записей. Найти нарушающих:

    SELECT u.login
    FROM users u
    JOIN user_roles ur ON ur.user_id = u.id
    JOIN roles r ON r.id = ur.role_id
    GROUP BY u.id, u.login
    HAVING (bool_or(r.code = 'USER') AND bool_or(r.code = 'STOREKEEPER'))
        OR (bool_or(r.code = 'STOREKEEPER') AND bool_or(r.code = 'ADMIN'));

Тестовые учётные записи (admin, store1, worker1, worker2, user1, user2) с одной ролью проблем не вызывают.
Скрипты и сценарии, которые создают пользователей с несколькими ролями, получат 409.

## Фронтенд
В форме ролей пользователя показывать текст ошибки `ROLE_COMBINATION_FORBIDDEN`; при желании отключать несовместимые
флажки заранее (можно отдать список запрещённых сочетаний отдельным эндпоинтом позже).

## Не сделано
- Проверка «заявитель не обрабатывает собственную заявку» в request-service (теперь невозможна при запрете
  USER + STOREKEEPER; для старых данных можно добавить защиту в `review`, `reserve` и `reject`).
- Принудительное снятие ролей у существующих пользователей.
