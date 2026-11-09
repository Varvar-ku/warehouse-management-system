import { api } from '../../api/client';
import { asArray } from '../../api/asArray';
import type { Page } from '../../api/types';
import type { PlacementTask } from '../tasks/api';
import type {
  CreateReceiptBody,
  ItemDecision,
  ItemRecommendation,
  Receipt,
  ReceiptStatus,
  ReceiptSummary,
  UpdateReceiptBody,
} from './types';

export interface ReceiptFilters {
  status?: ReceiptStatus;
  warehouseId?: string;
  q?: string;
  page: number;
  size: number;
}

export const listReceipts = (f: ReceiptFilters) =>
  api<Page<ReceiptSummary>>('/receipts', { query: { ...f } });

export const getReceipt = (id: string) => api<Receipt>(`/receipts/${id}`);

export const createReceipt = (body: CreateReceiptBody) => api<Receipt>('/receipts', { method: 'POST', body });

export const updateReceipt = (id: string, body: UpdateReceiptBody) =>
  api<Receipt>(`/receipts/${id}`, { method: 'PATCH', body });

/** Расчёт ничего не сохраняет решением: его можно вызывать повторно. */
export const recommendPlacement = async (id: string) =>
  asArray<ItemRecommendation>(await api<unknown>(`/receipts/${id}/recommend-placement`, { method: 'POST', body: {} }));

export const confirmReceipt = (id: string, decisions: ItemDecision[]) =>
  api<Receipt>(`/receipts/${id}/confirm`, { method: 'POST', body: decisions.length ? { decisions } : {} });

/** assigneeId не задан: задание создаётся без исполнителя. */
export const createPlacementTask = (id: string, assigneeId?: string) =>
  api<PlacementTask>(`/receipts/${id}/placement-tasks`, { method: 'POST', body: assigneeId ? { assigneeId } : {} });

export const cancelReceipt = (id: string, reason: string) =>
  api<Receipt>(`/receipts/${id}/cancel`, { method: 'POST', body: { reason } });

// ---- задания на размещение по приёмке (для кладовщика: видны все задания)

export const listPlacementTasksForReceipt = async (receiptId: string) =>
  asArray<PlacementTask>(await api<unknown>('/placement-tasks')).filter((t) => t.receiptId === receiptId);

/**
 * ACCEPT_ACTUAL: провести фактическое. RETURN_TO_PLACEMENT: вернуть на размещение (можно с новой секцией).
 * REJECT_LINE: закрыть позицию с нулевым приходом; допустимо и для ещё не размещённой (PENDING) позиции.
 */
export type ResolveDecision = 'ACCEPT_ACTUAL' | 'RETURN_TO_PLACEMENT' | 'REJECT_LINE';

export const resolveDiscrepancy = (
  taskId: string,
  itemId: string,
  body: { decision: ResolveDecision; newTargetSectionId?: string; comment: string },
) => api<PlacementTask>(`/placement-tasks/${taskId}/items/${itemId}/resolve`, { method: 'POST', body });
