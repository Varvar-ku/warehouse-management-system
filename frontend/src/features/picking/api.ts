import { api } from '../../api/client';
import { asArray } from '../../api/asArray';
import type {
  AddWorkersBody,
  AssignmentInput,
  CompleteTaskBody,
  CreatePickingTaskBody,
  PickingTask,
} from './types';

/** requestId передаётся как подсказка серверу; список всё равно фильтруется на клиенте (hooks.ts). */
export const listPickingTasks = async (requestId?: string) =>
  asArray<PickingTask>(await api<unknown>('/picking-tasks', { query: { requestId } }));

export const getPickingTask = (id: string) => api<PickingTask>(`/picking-tasks/${id}`);

export const createPickingTask = (body: CreatePickingTaskBody) =>
  api<PickingTask>('/picking-tasks', { method: 'POST', body });

export const addWorkers = (id: string, body: AddWorkersBody) =>
  api<PickingTask>(`/picking-tasks/${id}/workers`, { method: 'POST', body });

export const assignItems = (id: string, assignments: AssignmentInput[]) =>
  api<PickingTask>(`/picking-tasks/${id}/assign-items`, { method: 'POST', body: { assignments } });

/** replaceWith не задан: работник снимается, его строки становятся нераспределёнными. Причина обязательна. */
export const removeWorker = (id: string, workerId: string, reason: string, replaceWith?: string) =>
  api<PickingTask>(`/picking-tasks/${id}/workers/${workerId}`, {
    method: 'DELETE',
    query: { replaceWith, reason },
  });

export const completeTask = (id: string, body: CompleteTaskBody) =>
  api<PickingTask>(`/picking-tasks/${id}/complete`, { method: 'POST', body });
