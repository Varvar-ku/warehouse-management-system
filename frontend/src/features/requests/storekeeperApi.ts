import { api } from '../../api/client';
import type { RequestResponse } from './types';

export interface ItemApproval {
  itemId: string;
  approvedQuantity: number;
}

/**
 * Проверка наличия: SUBMITTED → APPROVED или PARTIALLY_AVAILABLE. Допустима и повторно из этих двух статусов:
 * количества обновляются, а если итоговый статус не изменился, история статусов не пополняется.
 */
export const reviewRequest = (id: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/review`, { method: 'POST', idempotencyKey });

/** Отклонение: из SUBMITTED, APPROVED и PARTIALLY_AVAILABLE. Осиротевший резерв бэкенд снимает сам. */
export const rejectRequest = (id: string, reason: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/reject`, { method: 'POST', body: { reason }, idempotencyKey });

/** items не передан: резервируется всё, что одобрено. Передан: решение кладовщика по количествам. */
export const reserveRequest = (id: string, items?: ItemApproval[], idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/reserve`, { method: 'POST', body: items ? { items } : {}, idempotencyKey });

export const markReadyForIssue = (id: string, comment?: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/ready-for-issue`, {
    method: 'POST',
    body: { comment: comment || null },
    idempotencyKey,
  });

/** issuedTo не передаётся: получателем становится автор заявки. */
export const issueRequest = (id: string, comment?: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/issue`, {
    method: 'POST',
    body: { issuedTo: null, comment: comment || null },
    idempotencyKey,
  });

export const closeRequest = (id: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/close`, { method: 'POST', idempotencyKey });
