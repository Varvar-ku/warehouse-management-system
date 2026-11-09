import { label } from '../picking/statuses';
import type { PlacementTask } from './api';

/* Значения сверены с PlacementTask.Status и ReceiptItem.PlacementStatus. */
export const PLACEMENT_TASK_STATUS: Record<string, string> = {
  OPEN: 'Открыто',
  COMPLETED: 'Выполнено',
  CANCELLED: 'Отменено',
};

export const PLACEMENT_ITEM_STATUS: Record<string, string> = {
  NEW: 'Новая',
  PENDING: 'Ожидает',
  PLACED: 'Размещено',
  DISCREPANCY: 'Расхождение',
  REJECTED: 'Отклонено',
};

export const placementTaskLabel = (s: string) => label(PLACEMENT_TASK_STATUS, s);
export const placementItemLabel = (s: string) => label(PLACEMENT_ITEM_STATUS, s);

export const isPlacementActive = (t: Pick<PlacementTask, 'status' | 'completedAt'>) =>
  !t.completedAt && t.status === 'OPEN';

/** Работник свою часть сделал: размещено, зафиксировано расхождение (дальше решает кладовщик) или позиция отклонена. */
export const isPlacementHandled = (status: string) =>
  status === 'PLACED' || status === 'DISCREPANCY' || status === 'REJECTED';
