# B1. Недостача при сборе (inventory-service)

Файлы собраны поверх версий из архивов B2 и B3 (Stock, StockRepository, StockQueryService взяты из B2).
Распаковывать в корень проекта с заменой. Миграции: **V8** (если уже применена, не менять) и **V9**.
Два сервиса: inventory-service и request-service. Порядок выкладки: сначала inventory, затем request.

## Как работает
1. `IssueService.confirm`: неиспользованная часть каждого резерва (`quantity - consumed`) превращается в
   недостачу `StockShortage` (OPEN), строка остатка получает `on_hold = true`.
2. Строка «на проверке» не попадает в резерв, `totals` (проверка наличия), `onlyAvailable` и не списывается.
3. Кладовщик решает по каждой недостаче:
   - `POST /api/v1/shortages/{id}/found` (комментарий обязателен): товар найден, блокировка снимается;
   - `POST /api/v1/adjustments` (из открытых недостач) -> `approve` (ADMIN, не автор) -> `confirm` (STOREKEEPER):
     остаток уменьшается, движение `ADJUSTMENT`, недостача закрыта, блокировка снимается.
4. Блокировка снимается, когда по строке не осталось открытых недостач.

## Новые файлы
- db/migration/V8__stock_shortage_hold.sql
- entity: StockShortage, StockAdjustment, StockAdjustmentItem
- repository: StockShortageRepository, StockAdjustmentRepository
- service: ShortageService, AdjustmentService, ShortageMapper
- dto: AdjustmentDtos
- controller: ShortageController, AdjustmentController
- test: StockRepositoryHoldIT, AdjustmentServiceTest

## Изменённые файлы
- entity/Stock: onHold, holdSince
- repository/StockRepository: `onHold = false` в totalsAt и findAvailableIdsOrderedAt
- service/StockQueryService: фильтр onlyAvailable
- service/IssueService: регистрация недостач при confirm
- service/ReservationService: пропуск строки, ушедшей «на проверку» между выбором и блокировкой
- service/WriteOffService: запрет списания строки «на проверке» (STOCK_ON_HOLD)
- dto/InventoryDtos, service/InventoryMapper: StockResponse.onHold

## Часть 2: точная привязка и отмена
- Комплектация сообщает, какие резервы не собраны: `ShortageMark(reservationId, quantity)`.
- `POST /issues` (CreateIssueRequest.shortages): выдаваемое количество раскладывается по резервам с учётом недобора,
  поэтому блокируется именно та строка остатка, где не хватило товара.
- `POST /reservations/release` (ReleaseRequest.shortages): при снятии резерва недобор регистрируется как недостача
  (источник CANCELLATION), а не возвращается в доступный остаток.
- request-service: IssuingService передаёт недобор в createIssue. **Отмена заявки (RequestService.cancel) ещё не
  передаёт отметки**: нужны RequestService, InventoryClient, PickingItem, PickingTaskRepository.
- Новое: V9, ShortageMark, StockShortage.source, ReservationReleaseShortageTest.

## Нужно сделать вне архива
- gateway: маршруты `/shortages/**` и `/adjustments/**` (как у `/write-offs/**`).
- IdempotencyFilter: добавить `POST /adjustments/{id}/confirm` (как confirm списания), если пути перечислены явно.
- report-service: проверить, что движение ADJUSTMENT с одним `fromSection` уменьшает остаток.
- Если где-то (тесты, другие модули) вызывается конструктор `StockResponse`, добавить последний аргумент.
