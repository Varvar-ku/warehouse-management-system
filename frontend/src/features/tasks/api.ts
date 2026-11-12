import { api } from '../../api/client';
import { asArray } from '../../api/asArray';
import type { PickingTask } from '../picking/types';

// ---------- комплектация ----------

/** Принять задание: статус работника ACCEPTED (PickingTaskWorker.Status). */
export const WORKER_ACCEPT_STATUS = 'ACCEPTED';

export const acceptPickingTask = (taskId: string, workerId: string) =>
  api<PickingTask>(`/picking-tasks/${taskId}/workers/${workerId}`, {
    method: 'PATCH',
    body: { status: WORKER_ACCEPT_STATUS },
  });

export const pickRow = (taskId: string, rowId: string, pickedQuantity: number, comment?: string) =>
  api<PickingTask>(`/picking-tasks/${taskId}/items/${rowId}/pick`, {
    method: 'POST',
    body: { pickedQuantity, comment: comment || null },
  });

// ---------- размещение ----------

export interface PlacementItem {
  itemId: string;
  lineNo: number;
  productId: string;
  productSku: string;
  productName: string;
  unitCode: string;
  quantity: number;
  targetSectionId: string | null;
  targetSectionCode: string | null;
  placementStatus: string;
  placedSectionCode: string | null;
  placedQuantity: number | null;
  discrepancyComment: string | null;
}

export interface PlacementTask {
  id: string;
  receiptId: string;
  receiptNumber: string;
  warehouseId: string;
  status: string;
  /** Первый из исполнителей (поле оставлено для совместимости). */
  assigneeId: string | null;
  /** Исполнители размещения; пусто: задание видят все работники склада. */
  workerIds: string[];
  createdAt: string;
  completedAt: string | null;
  items: PlacementItem[];
}

export const listPlacementTasks = async (status?: string) =>
  asArray<PlacementTask>(await api<unknown>('/placement-tasks', { query: { status } }));

export const getPlacementTask = (id: string) => api<PlacementTask>(`/placement-tasks/${id}`);

export const confirmPlacement = (taskId: string, itemId: string, sectionId: string, quantity: number, comment?: string) =>
  api<unknown>(`/placement-tasks/${taskId}/items/${itemId}/confirm`, {
    method: 'POST',
    body: { sectionId, quantity, comment: comment || null },
  });

// ---------- секции (для выбора фактической секции) ----------

export interface SectionOption {
  id: string;
  code: string;
  status?: string;
  unassignedZone?: boolean;
}

/**
 * placeableOnly: для размещения товара. Бэкенд принимает только активные секции и не принимает зону «Нераспределено»
 * (кроме случая, когда кладовщик сам назначил её позиции), поэтому остальные в выборе не показываем.
 */
export const searchSections = async (warehouseId: string, q: string, placeableOnly = false) => {
  const found = asArray<SectionOption>(
    await api<unknown>('/sections', {
      query: { warehouseId, q: q || undefined, status: placeableOnly ? 'ACTIVE' : undefined, size: 20 },
    }),
  );
  return placeableOnly ? found.filter((s) => !s.unassignedZone) : found;
};
