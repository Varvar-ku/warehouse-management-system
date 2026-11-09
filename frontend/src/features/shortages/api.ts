import { api } from '../../api/client';
import type { Page } from '../../api/types';
import type { Shortage, ShortageStatus } from './types';

export interface ShortageFilters {
  status?: ShortageStatus;
  warehouseId?: string;
  page: number;
  size: number;
}

export const listShortages = (f: ShortageFilters) => api<Page<Shortage>>('/shortages', { query: { ...f } });

/** «Найдено»: недостачи нет, остаток возвращается в оборот. Комментарий обязателен. Реальную недостачу закрывает корректировка. */
export const markShortageFound = (id: string, comment: string) =>
  api<Shortage>(`/shortages/${id}/found`, { method: 'POST', body: { comment } });
