import type { RequestStatus } from '../../shared/statuses';

export type StorekeeperAction =
  | 'review'
  | 'reject'
  | 'reserve'
  | 'reserve-partial'
  | 'create-task'
  | 'ready'
  | 'issue'
  | 'close'
  | 'cancel';

export interface ActionContext {
  /**
   * По заявке не собрано ни одной единицы (известно из задания комплектации). undefined: данные ещё не загружены.
   * Нужно для поздних статусов: оформлять и выдавать нечего, остаётся только отмена.
   */
  nothingPicked?: boolean;
}

/**
 * Какие кнопки показать кладовщику в каждом статусе (зеркало RequestStatus и RequestService бэкенда).
 * Это подсказка интерфейсу: окончательное решение принимает бэкенд (INVALID_TRANSITION, CANCEL_NOT_ALLOWED,
 * REJECT_NOT_ALLOWED).
 *
 * - Промежуточного UNDER_REVIEW больше нет: проверка наличия сразу даёт APPROVED или PARTIALLY_AVAILABLE
 *   и допустима повторно из любого из этих статусов (обновляет количества).
 * - Отклонить можно, пока резерва нет: из SUBMITTED, APPROVED и PARTIALLY_AVAILABLE. После резерва только отмена.
 * - Собранное не отменяется (ТЗ): в PICKED отмены нет. В READY_FOR_ISSUE отмена возможна только
 *   если ничего не собрано, иначе заявка осталась бы с резервом без возможности выдачи.
 */
export function storekeeperActions(status: RequestStatus, ctx: ActionContext = {}): StorekeeperAction[] {
  switch (status) {
    case 'SUBMITTED':
      return ['review', 'reject', 'cancel'];
    case 'APPROVED':
      return ['reserve', 'review', 'reject', 'cancel'];
    case 'PARTIALLY_AVAILABLE':
      return ['reserve-partial', 'review', 'reject', 'cancel'];
    case 'RESERVED':
    case 'RESERVED_PARTIAL':
      return ['create-task', 'cancel'];
    case 'PICKING':
      return ['cancel'];
    case 'PICKED':
      return ['ready'];
    case 'PICKING_WITH_SHORTAGE':
      return ctx.nothingPicked ? ['cancel'] : ['ready', 'cancel'];
    case 'READY_FOR_ISSUE':
      return ctx.nothingPicked ? ['cancel'] : ['issue'];
    case 'REJECTED':
    case 'CANCELLED':
      return ['close'];
    default:
      return [];
  }
}

/** Статусы, в которых имеет смысл блок «Доступность»: до резерва. */
export const AVAILABILITY_STATUSES: RequestStatus[] = ['APPROVED', 'PARTIALLY_AVAILABLE'];

/** Статусы, в которых у заявки уже могут быть резервы. */
export const RESERVATION_STATUSES: RequestStatus[] = [
  'RESERVED',
  'RESERVED_PARTIAL',
  'PICKING',
  'PICKED',
  'PICKING_WITH_SHORTAGE',
  'READY_FOR_ISSUE',
  'ISSUED',
  'CLOSED',
];

/** Статусы, в которых показывается блок комплектации. */
export const PICKING_STATUSES: RequestStatus[] = [
  'PICKING',
  'PICKED',
  'PICKING_WITH_SHORTAGE',
  'READY_FOR_ISSUE',
  'ISSUED',
  'CLOSED',
];
