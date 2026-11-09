import { api } from '../../api/client';
import type { Page } from '../../api/types';
import type {
  CreateRequestBody,
  HistoryEntry,
  RequestFilters,
  RequestResponse,
  RequestSummary,
  UpdateRequestBody,
} from './types';

export const listRequests = (f: RequestFilters) => api<Page<RequestSummary>>('/requests', { query: { ...f } });

export const getRequest = (id: string) => api<RequestResponse>(`/requests/${id}`);

export const getHistory = (id: string) => api<HistoryEntry[]>(`/requests/${id}/history`);

/** idempotencyKey защищает от дубля черновика при двойном клике и повторе после сбоя сети. */
export const createRequest = (body: CreateRequestBody, idempotencyKey?: string) =>
  api<RequestResponse>('/requests', { method: 'POST', body, idempotencyKey });

export const updateRequest = (id: string, body: UpdateRequestBody) =>
  api<RequestResponse>(`/requests/${id}`, { method: 'PATCH', body });

export const deleteDraft = (id: string) => api<void>(`/requests/${id}`, { method: 'DELETE' });

export const submitRequest = (id: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/submit`, { method: 'POST', idempotencyKey });

export const cancelRequest = (id: string, reason: string, idempotencyKey?: string) =>
  api<RequestResponse>(`/requests/${id}/cancel`, { method: 'POST', body: { reason }, idempotencyKey });
