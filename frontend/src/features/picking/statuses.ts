import type { PickingTask } from './types';

/* Значения enum сверены с PickingTask.Status, PickingTaskWorker.Status/Role и PickingItem.Status. */
export const TASK_STATUS: Record<string, string> = {
  NEW: 'Новое',
  IN_PROGRESS: 'В работе',
  COMPLETED: 'Завершено',
  REJECTED: 'Отклонено',
};

export const WORKER_STATUS: Record<string, string> = {
  ASSIGNED: 'Назначен',
  ACCEPTED: 'Принял',
  IN_PROGRESS: 'Собирает',
  COMPLETED: 'Закончил',
  CANCELLED: 'Снят',
  REASSIGNED: 'Заменён',
};

export const WORKER_ROLE: Record<string, string> = {
  PRIMARY: 'Ответственный',
  WORKER: 'Сборщик',
};

export const ITEM_STATUS: Record<string, string> = {
  PENDING: 'Ожидает',
  PICKED: 'Собрана',
  SHORTAGE: 'Недобор',
};

export const label = (dict: Record<string, string>, key: string) => dict[key] ?? key;

const FINISHED_TASK = ['COMPLETED', 'REJECTED'];
const INACTIVE_WORKER = ['COMPLETED', 'CANCELLED', 'REASSIGNED'];

/** Задание ещё не завершено: можно распределять строки, добавлять и менять работников. */
export const isTaskActive = (t: Pick<PickingTask, 'status' | 'completedAt'>) =>
  !t.completedAt && !FINISHED_TASK.includes(t.status);

/** Работник ещё участвует в задании (его можно заменить или убрать). */
export const isWorkerActive = (status: string, completedAt: string | null) =>
  !completedAt && !INACTIVE_WORKER.includes(status);

/** Строка сбора обработана работником (собрана полностью или с недобором). */
export const isItemProcessed = (status: string) => status !== 'PENDING';
