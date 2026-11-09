import { api } from '../../api/client';
import type { Page } from '../../api/types';
import type { CreateWriteOffBody, WriteOff, WriteOffReason, WriteOffStatus } from './types';

export interface WriteOffFilters {
  status?: WriteOffStatus;
  reason?: WriteOffReason;
  page: number;
  size: number;
}

export const listWriteOffs = (f: WriteOffFilters) => api<Page<WriteOff>>('/write-offs', { query: { ...f } });

export const getWriteOff = (id: string) => api<WriteOff>(`/write-offs/${id}`);

export const createWriteOff = (body: CreateWriteOffBody) => api<WriteOff>('/write-offs', { method: 'POST', body });

/** Одобряет администратор, если списание требует одобрения. */
export const approveWriteOff = (id: string) => api<WriteOff>(`/write-offs/${id}/approve`, { method: 'POST' });

/** Проведение уменьшает остаток: Idempotency-Key обязателен, повтор с тем же ключом не спишет дважды. */
export const confirmWriteOff = (id: string, idempotencyKey: string) =>
  api<WriteOff>(`/write-offs/${id}/confirm`, { method: 'POST', idempotencyKey });

export const cancelWriteOff = (id: string, reason: string) =>
  api<WriteOff>(`/write-offs/${id}/cancel`, { method: 'POST', body: { reason } });
