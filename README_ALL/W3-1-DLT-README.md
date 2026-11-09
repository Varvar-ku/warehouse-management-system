# Волна 3, пункт 1: возврат «мёртвых» сообщений (DLT)

## Проблема
После трёх повторов с паузой в 1 с потребители (audit, report, notification, inventory) отправляют сообщение в
`<топик>.DLT`, но читателя этого топика не было: сбой БД на несколько секунд навсегда терял запись аудита, факт
движения (искажал отчёт «Остатки») или уведомление.

## Что сделано (common-lib, пакет `ru.warehouse.common.event.dlt`)
- `DltRedriver`: читает DLT своей группой (`wms-dlt-redrive-<имя сервиса>`, позиция хранится в Kafka) и возвращает каждое
  сообщение старше `min-age-ms` в исходный топик с тем же ключом и счётчиком попыток в заголовке `x-wms-redrive-count`.
  Если сообщение снова не обработано, оно вернётся в DLT с большим счётчиком; после `max-redrives` попыток оно
  «паркуется»: остаётся в DLT, в лог пишется ERROR, автоматически больше не возвращается (разбирать вручную).
- `DltRedriveScheduler`: прогон раз в 5 минут (первый через минуту).
- `DltAutoConfiguration`: включается свойством `wms.dlt.redrive.enabled=true`.

## ОБЯЗАТЕЛЬНО вручную (файл не был в загрузках)
Добавьте строку в файл
`common-lib/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

    ru.warehouse.common.event.dlt.DltAutoConfiguration

## Включение в сервисах (application.yaml)
На каждый DLT должен возвращать ровно один сервис (DLT называется по исходному топику, а не по группе, поэтому
DLT топика `wms.audit.v1` общий для audit, report и notification):

    # audit-service
    wms:
      dlt:
        redrive:
          enabled: true
          topics: wms.audit.v1

    # report-service
    wms:
      dlt:
        redrive:
          enabled: true
          topics: wms.stock-movements.v1

    # inventory-service (топик статуса секции: значение SectionStatusEvent.TOPIC)
    wms:
      dlt:
        redrive:
          enabled: true
          topics: <SectionStatusEvent.TOPIC>

Notification-service читает тот же `wms.audit.v1` и тестовый `request-events`: возврат для `wms.audit.v1` делает
audit-service, поэтому notification включайте только для `request-events` (в основной конфигурации не нужно).
Необязательные настройки: `max-redrives` (5), `min-age-ms` (60000), `interval-ms` (300000), `initial-delay-ms` (60000).

## Как это работает с дублями
Возвращённое сообщение увидят все группы потребителей исходного топика (audit, report и notification для
`wms.audit.v1`). Они отбрасывают дубли по `event_id`, поэтому повтор безвреден, а упавший потребитель наконец
обрабатывает своё сообщение.

## Проверить
- Остановите БД audit-service, отправьте событие (например, создайте склад), через несколько секунд запустите БД:
  запись должна появиться в журнале аудита не позднее чем через 5–6 минут (без этого она оставалась бы в DLT).
- Парковка: сообщение с неподдерживаемой версией схемы (`IllegalArgumentException`, не повторяется) возвращается
  максимум 5 раз, затем в логе ERROR «запарковано».
- DLT и исходный топик должны иметь одинаковое число партиций (DeadLetterPublishingRecoverer пишет в ту же партию).

## Ограничения
- Возврат идёт в исходный топик целиком, а не только упавшей группе (это свойство Kafka без отдельных retry-топиков).
- Метрик и health-индикатора нет: состояние видно по логам (`DLT: возвращено`, `запарковано`) и по длине DLT-топика.
