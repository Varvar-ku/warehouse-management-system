import { api } from '../../../api/client';
import { asArray } from '../../../api/asArray';
import type { Page, UserResponse } from '../../../api/types';

export interface RoleOption {
  code: string;
  name: string;
}

export interface CreateUserBody {
  login: string;
  password: string;
  fullName: string;
  email?: string;
  phone?: string;
  roles: string[];
}

/** PATCH: не переданное поле не меняется; roles заменяет набор ролей целиком. */
export interface UpdateUserBody {
  fullName?: string;
  email?: string;
  phone?: string;
  active?: boolean;
  roles?: string[];
}

export const listUsers = (page: number, size: number) => api<Page<UserResponse>>('/users', { query: { page, size } });

export const listRoles = async () => asArray<RoleOption>(await api<unknown>('/roles'));

export const createUser = (body: CreateUserBody) => api<UserResponse>('/users', { method: 'POST', body });

export const updateUser = (id: string, body: UpdateUserBody) => api<UserResponse>(`/users/${id}`, { method: 'PATCH', body });

export const resetUserPassword = (id: string, newPassword: string) =>
  api<void>(`/users/${id}/reset-password`, { method: 'POST', body: { newPassword } });
