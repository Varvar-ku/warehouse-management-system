import type { Role } from '../api/types';

export const ROLE_LABELS: Record<Role, string> = {
  USER: 'Пользователь',
  STOREKEEPER: 'Кладовщик',
  WAREHOUSE_WORKER: 'Работник склада',
  ADMIN: 'Администратор',
};

/** Стартовая страница после входа. */
export function homePath(roles: Role[]): string {
  if (roles.includes('STOREKEEPER') || roles.includes('USER')) return '/requests';
  if (roles.includes('WAREHOUSE_WORKER')) return '/tasks';
  return '/admin/users';
}
