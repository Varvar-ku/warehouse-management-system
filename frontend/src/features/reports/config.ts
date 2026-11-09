import { fmtDateTime, fmtQty } from '../../shared/format';
import { REQUEST_STATUS, type RequestStatus } from '../../shared/statuses';
import { MOVEMENT_TYPE, MOVEMENT_TYPE_OPTIONS } from '../stock/labels';
import type { MovementType } from '../stock/api';
import { WRITE_OFF_REASON } from '../writeoffs/statuses';
import type { WriteOffReason } from '../writeoffs/types';
import type { FilterDef, ReportColumn, ReportDef, Row } from './types';

const str = (v: unknown) => (v === null || v === undefined ? '' : String(v));
const dash = (v: unknown) => (str(v) === '' ? '—' : str(v));

const txt = (title: string, key: string): ReportColumn => ({ title, value: (r) => dash(r[key]), raw: (r) => str(r[key]) });
const dt = (title: string, key: string): ReportColumn => ({
  title,
  value: (r) => (r[key] ? fmtDateTime(String(r[key])) : '—'),
  raw: (r) => (r[key] ? new Date(String(r[key])).toLocaleString('ru-RU') : ''),
});
/** Количества во всех отчётах в штуках: единицу в заголовке указываем один раз, отдельной колонки нет. */
const qty = (title: string, key: string): ReportColumn => ({
  title: `${title}, шт`,
  align: 'right',
  value: (r) => (r[key] === null || r[key] === undefined ? '—' : fmtQty(Number(r[key]))),
  raw: (r) => (r[key] === null || r[key] === undefined ? '' : String(r[key]).replace('.', ',')),
});
const lab = (title: string, key: string, dict: Record<string, string>): ReportColumn => ({
  title,
  value: (r) => (r[key] ? (dict[String(r[key])] ?? String(r[key])) : '—'),
  raw: (r) => (r[key] ? (dict[String(r[key])] ?? String(r[key])) : ''),
});
/** Человек: имя из отчёта; если справочник был недоступен, остаётся пусто. */
const who = (title: string, nameKey: string): ReportColumn => ({
  title,
  value: (r) => dash(r[nameKey]),
  raw: (r) => str(r[nameKey]),
});
const product = (r: Row) => [r.productSku, r.productName].filter(Boolean).join(' — ');
const prod: ReportColumn = { title: 'Товар', value: (r) => dash(product(r)), raw: product };

const movementLabels = Object.fromEntries(
  (Object.keys(MOVEMENT_TYPE) as MovementType[]).map((t) => [t, MOVEMENT_TYPE[t].label]),
);
const reasonLabels = WRITE_OFF_REASON as Record<WriteOffReason, string>;
const requestLabels = Object.fromEntries(
  (Object.keys(REQUEST_STATUS) as RequestStatus[]).map((s) => [s, REQUEST_STATUS[s].label]),
);
const SOURCE_LABELS: Record<string, string> = { REQUEST: 'Заявка', RECEIPT: 'Приёмка' };
const RESOLUTION_LABELS: Record<string, string> = {
  ACCEPT_ACTUAL: 'Принято фактическое',
  RETURN_TO_PLACEMENT: 'Возврат на размещение',
  REJECT_LINE: 'Позиция отклонена',
};

const sku: FilterDef = { kind: 'text', name: 'sku', label: 'Артикул' };
const batch: FilterDef = { kind: 'text', name: 'batchNumber', label: 'Партия' };
const doc: FilterDef = { kind: 'text', name: 'documentNumber', label: 'Номер документа' };
const wh: FilterDef = { kind: 'warehouse', name: 'warehouseId' };
const performedBy: FilterDef = { kind: 'uuid', name: 'performedBy', label: 'UUID исполнителя' };
const range: FilterDef = { kind: 'range' };

export const REPORTS: ReportDef[] = [
  {
    key: 'stock',
    group: 'Склад',
    title: 'Остатки',
    path: '/reports/stock',
    description:
      'Физический остаток по журналу движений на конец выбранных суток; резерв в него не входит. Для сверки с учётом откройте раздел «Остатки».',
    filters: [{ kind: 'date', name: 'asOf', label: 'На дату' }, wh, sku, batch, { kind: 'bool', name: 'includeZero', label: 'С нулевыми' }],
    columns: [
      txt('Секция', 'sectionCode'),
      prod,
      txt('Партия', 'batchNumber'),
      dt('Годен до', 'expiryAt'),
      qty('Количество', 'quantity'),
    ],
  },
  {
    key: 'movements',
    group: 'Склад',
    title: 'Движение детали',
    path: '/reports/stock-movements',
    filters: [range, wh, sku, batch, doc, { kind: 'select', name: 'type', label: 'Тип движения', options: MOVEMENT_TYPE_OPTIONS }, performedBy],
    columns: [
      dt('Время', 'performedAt'),
      lab('Тип', 'movementType', movementLabels),
      prod,
      txt('Партия', 'batchNumber'),
      qty('Количество', 'quantity'),
      txt('Откуда', 'fromSection'),
      txt('Куда', 'toSection'),
      txt('Документ', 'documentNumber'),
      who('Исполнитель', 'performedByName'),
    ],
  },
  {
    key: 'receipts',
    group: 'Склад',
    title: 'Поступления',
    path: '/reports/receipts',
    filters: [range, wh, sku, batch, doc, performedBy],
    columns: [
      dt('Время', 'performedAt'),
      txt('Документ', 'documentNumber'),
      txt('Поставщик', 'supplierName'),
      txt('Внешний номер', 'externalDocumentNumber'),
      prod,
      txt('Партия', 'batchNumber'),
      qty('Количество', 'quantity'),
      txt('Рекомендовано', 'recommendedSection'),
      txt('Размещено', 'placedSection'),
      who('Исполнитель', 'performedByName'),
    ],
  },
  {
    key: 'issues',
    group: 'Склад',
    title: 'Выдачи',
    path: '/reports/issues',
    filters: [range, wh, sku, batch, doc, performedBy],
    columns: [
      dt('Время', 'performedAt'),
      txt('Документ', 'documentNumber'),
      who('Получатель', 'issuedToName'),
      prod,
      txt('Партия', 'batchNumber'),
      qty('Количество', 'quantity'),
      txt('Секция', 'section'),
      who('Исполнитель', 'performedByName'),
    ],
  },
  {
    key: 'write-offs',
    group: 'Склад',
    title: 'Списания',
    path: '/reports/write-offs',
    filters: [range, wh, sku, batch, doc, performedBy],
    columns: [
      dt('Время', 'performedAt'),
      txt('Документ', 'documentNumber'),
      lab('Причина', 'reason', reasonLabels),
      prod,
      txt('Партия', 'batchNumber'),
      txt('Секция', 'section'),
      qty('Количество', 'quantity'),
      who('Создал', 'createdByName'),
      txt('Комментарий', 'comment'),
    ],
  },
  {
    key: 'requests',
    group: 'Заявки',
    title: 'Заявки',
    path: '/reports/requests',
    description: 'Период отбирает заявки по дате создания.',
    filters: [
      range,
      wh,
      { kind: 'text', name: 'number', label: 'Номер заявки' },
      {
        kind: 'select',
        name: 'status',
        label: 'Статус',
        options: (Object.keys(REQUEST_STATUS) as RequestStatus[])
          .filter((s) => s !== 'UNDER_REVIEW')
          .map((s) => ({ value: s, label: REQUEST_STATUS[s].label })),
      },
      { kind: 'uuid', name: 'requesterId', label: 'UUID заявителя' },
    ],
    columns: [
      txt('Номер', 'number'),
      who('Заявитель', 'requesterName'),
      lab('Статус', 'status', requestLabels),
      { title: 'Позиций', align: 'right', value: (r) => dash(r.itemsCount), raw: (r) => str(r.itemsCount) },
      qty('Запрошено', 'requestedQuantity'),
      qty('Одобрено', 'approvedQuantity'),
      qty('Зарезервировано', 'reservedQuantity'),
      qty('Выдано', 'issuedQuantity'),
      dt('Создана', 'createdAt'),
      dt('Закрыта', 'closedAt'),
      txt('Выдача', 'issueNumber'),
    ],
  },
  {
    key: 'discrepancies',
    group: 'Заявки',
    title: 'Расхождения',
    path: '/reports/discrepancies',
    description:
      'Заявки: запрошено и выдано. Приёмки: по плану размещения и фактически. Отклонение равно факту минус требовалось.',
    filters: [
      range,
      {
        kind: 'select',
        name: 'source',
        label: 'Источник',
        options: [
          { value: 'REQUEST', label: 'Заявка' },
          { value: 'RECEIPT', label: 'Приёмка' },
        ],
      },
      doc,
      sku,
      { kind: 'uuid', name: 'responsibleId', label: 'UUID ответственного' },
      { kind: 'bool', name: 'unresolvedOnly', label: 'Только нерешённые приёмки' },
    ],
    columns: [
      dt('Время', 'occurredAt'),
      lab('Источник', 'source', SOURCE_LABELS),
      txt('Документ', 'documentNumber'),
      { title: 'Строка', align: 'right', value: (r) => dash(r.lineNo), raw: (r) => str(r.lineNo) },
      prod,
      qty('Требовалось', 'requiredQuantity'),
      qty('Фактически', 'actualQuantity'),
      qty('Отклонение', 'deviation'),
      txt('Причина', 'reason'),
      who('Ответственный', 'responsibleName'),
      txt('План. секция', 'plannedSection'),
      txt('Факт. секция', 'actualSection'),
      lab('Решение', 'resolution', RESOLUTION_LABELS),
      txt('Комментарий решения', 'resolutionComment'),
      who('Решил', 'resolvedByName'),
    ],
  },
  {
    key: 'picking-events',
    group: 'Комплектация',
    title: 'Журнал комплектации',
    path: '/reports/picking/events',
    filters: [range, { kind: 'worker', name: 'workerId' }, { kind: 'text', name: 'taskNumber', label: 'Номер задания' }, { kind: 'text', name: 'action', label: 'Действие' }],
    columns: [
      dt('Время', 'occurredAt'),
      txt('Действие', 'action'),
      txt('Задание', 'taskNumber'),
      who('Кто', 'actorName'),
      who('Работник', 'workerName'),
      who('Замена', 'replaceWithName'),
      txt('Заявка', 'requestNumber'),
      { title: 'Строка', align: 'right', value: (r) => dash(r.lineNo), raw: (r) => str(r.lineNo) },
      txt('Секция', 'sectionCode'),
      txt('Партия', 'batchNumber'),
      qty('Назначено', 'quantity'),
      qty('Собрано', 'pickedQuantity'),
      { title: 'Недобор', value: (r) => (r.shortage ? 'да' : r.shortage === false ? 'нет' : '—'), raw: (r) => (r.shortage ? 'да' : '') },
      txt('Примечание', 'note'),
    ],
  },
  {
    key: 'picking-workers',
    group: 'Комплектация',
    title: 'Сводка по работникам',
    path: '/reports/picking/workers',
    filters: [range, { kind: 'worker', name: 'workerId' }, { kind: 'text', name: 'taskNumber', label: 'Номер задания' }],
    columns: [
      who('Работник', 'workerName'),
      { title: 'Заданий', align: 'right', value: (r) => dash(r.tasks), raw: (r) => str(r.tasks) },
      { title: 'Строк обработано', align: 'right', value: (r) => dash(r.rowsProcessed), raw: (r) => str(r.rowsProcessed) },
      qty('Назначено', 'assignedQuantity'),
      qty('Собрано', 'pickedQuantity'),
      { title: 'Строк с недобором', align: 'right', value: (r) => dash(r.shortageRows), raw: (r) => str(r.shortageRows) },
      dt('Первый сбор', 'firstPickAt'),
      dt('Последний сбор', 'lastPickAt'),
      { title: 'Снятий', align: 'right', value: (r) => dash(r.removals), raw: (r) => str(r.removals) },
      qty('Передано', 'movedQuantity'),
    ],
  },
];

export const findReport = (key: string | undefined) => REPORTS.find((r) => r.key === key) ?? REPORTS[0];
