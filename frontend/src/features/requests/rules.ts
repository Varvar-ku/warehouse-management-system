import type { RequestStatus } from '../../shared/statuses';

/**
 * Статусы, в которых пользователь может отменить свою заявку: только до резерва.
 * После резерва отменяет кладовщик (резерв при этом освобождается).
 * Это подсказка для кнопки: окончательное решение за бэкендом (ошибка CANCEL_NOT_ALLOWED).
 */
export const USER_CANCELLABLE: RequestStatus[] = ['SUBMITTED', 'APPROVED', 'PARTIALLY_AVAILABLE'];
