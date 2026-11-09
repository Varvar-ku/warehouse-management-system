import { api } from '../../api/client';
import type { Page } from '../../api/types';
import type { Adjustment, AdjustmentStatus, CreateAdjustmentBody } from './types';

export interface AdjustmentFilters {
  status?: AdjustmentStatus;
  page: number;
  size: number;
}

export const listAdjustments = (f: AdjustmentFilters) => api<Page<Adjustment>>('/adjustments', { query: { ...f } });

export const getAdjustment = (id: string) => api<Adjustment>(`/adjustments/${id}`);

/** Оформляет кладовщик. Недостача может входить только в один неотменённый документ (SHORTAGE_IN_ADJUSTMENT). */
export const createAdjustment = (body: CreateAdjustmentBody) => api<Adjustment>('/adjustments', { method: 'POST', body });

/** Одобрение обязательно; одобряет администратор, но не автор документа (SELF_APPROVAL). */
export const approveAdjustment = (id: string) => api<Adjustment>(`/adjustments/${id}/approve`, { method: 'POST' });

/** Проведение уменьшает остаток: повтор с тем же Idempotency-Key не спишет недостачу дважды. */
export const confirmAdjustment = (id: string, idempotencyKey: string) =>
  api<Adjustment>(`/adjustments/${id}/confirm`, { method: 'POST', idempotencyKey });

export const cancelAdjustment = (id: string, reason: string) =>
  api<Adjustment>(`/adjustments/${id}/cancel`, { method: 'POST', body: { reason } });
