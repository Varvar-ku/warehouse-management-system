# B6, B7 и B1 (часть 2): статусы заявки, уведомления, недобор при отмене

Сервисы: request-service, notification-service. Порядок выкладки: inventory (B1, B4), затем request, затем notification.
Решение владельца: **UNDER_REVIEW убран из модели**.

## B6. request-service
- `entity/RequestStatus`: новая таблица переходов.
  - SUBMITTED -> APPROVED, PARTIALLY_AVAILABLE, REJECTED, CANCELLED
  - APPROVED -> RESERVED, PARTIALLY_AVAILABLE (повторная проверка), REJECTED, CANCELLED
  - PARTIALLY_AVAILABLE -> RESERVED_PARTIAL, APPROVED (повторная проверка), REJECTED, CANCELLED
  - UNDER_REVIEW: переходов нет, константа `@Deprecated` и оставлена **только** ради старых строк неизменяемой истории.
- `RequestService.review`: прямой переход в итоговый статус; повторная проверка без смены статуса пишет аудит
  `request.rechecked` и не пополняет историю.
- `RequestService.reject`: из SUBMITTED, APPROVED, PARTIALLY_AVAILABLE, иначе `REJECT_NOT_ALLOWED`.
- test/RequestStatusTest: обновлён (в том числе проверка, что в UNDER_REVIEW и из него переходов нет).

## B7. notification-service
- Типы `REQUEST_REJECTED`, `REQUEST_CANCELLED`; миграция `V2__notification_types_rejected_cancelled.sql`
  (CHECK `ck_notifications_type` из V1 расширен). Если в проекте уже есть V2, возьмите следующий номер.
- Заявитель, отменивший заявку сам, уведомления об этом не получает: `request.status_changed` теперь несёт `actorId`
  в `details` (RequestService.transition), `EventEnvelope.Details` читает его.
- Не сделано: уведомления кладовщику о новых заявках и работнику о назначенном задании.

## B1, часть 2: отмена заявки передаёт недобор
- `RequestService.cancel`: перед снятием резерва собирает отметки по строкам SHORTAGE (открытые и завершённые задания,
  `IssuingService.shortageMarks`) и передаёт их в `inventoryClient.release(requestId, marks)`.
  Недобор попадает в inventory как недостача (источник CANCELLATION), строка остатка уходит «на проверку».
- `InventoryClient.release(UUID, List<ShortageMark>)`; старая форма `release(UUID)` сохранена.

## Остальное в архиве: B4 (см. B4-README.md)

## Проверить
- Фронтенд: убрать UNDER_REVIEW из словаря статусов и фильтров; «Отклонить» доступна в SUBMITTED, APPROVED,
  PARTIALLY_AVAILABLE; новые типы уведомлений.
- Сценарии `scenario-5a/5b` (review без промежуточного статуса), `scenario-7-notifications.ps1`.
- Тест маппера уведомлений: если он проверяет, что REJECTED/CANCELLED игнорируются, обновить.
- Отмена после сборки с недобором: в inventory появляется открытая недостача (`GET /shortages`), резерв снят.
