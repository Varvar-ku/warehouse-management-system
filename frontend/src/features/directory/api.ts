import { api } from '../../api/client';
import { asArray } from '../../api/asArray';

export interface UserInfo {
  id: string;
  login: string;
  fullName: string;
  active: boolean;
}

export interface WorkerInfo {
  id: string;
  login: string;
  fullName: string;
}

/** Доступно кладовщику и администратору. */
export const listUsersByIds = async (ids: string[]) =>
  asArray<UserInfo>(await api<unknown>('/directory/users', { query: { ids } }));

export const listWorkers = async () => asArray<WorkerInfo>(await api<unknown>('/directory/workers'));
