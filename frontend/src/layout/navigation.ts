import type { Role } from '../api/types';

export interface NavItem {
  path: string;
  label: string;
  roles: Role[];
}

/** Единственное место, где описаны разделы и роли. Меню и маршруты строятся из этого списка. */
export const NAV: NavItem[] = [
  { path: '/requests', label: 'Заявки', roles: ['USER', 'STOREKEEPER', 'ADMIN'] },
  { path: '/notifications', label: 'Уведомления', roles: ['USER', 'STOREKEEPER', 'ADMIN', 'WAREHOUSE_WORKER'] },
  { path: '/picking', label: 'Комплектация', roles: ['STOREKEEPER'] },
  { path: '/tasks', label: 'Мои задания', roles: ['WAREHOUSE_WORKER'] },
  { path: '/receipts', label: 'Приёмка', roles: ['STOREKEEPER'] },
  { path: '/issues', label: 'Выдачи', roles: ['STOREKEEPER'] },
  { path: '/stock', label: 'Остатки', roles: ['STOREKEEPER', 'ADMIN'] },
  { path: '/shortages', label: 'Недостачи', roles: ['STOREKEEPER', 'ADMIN'] },
  { path: '/adjustments', label: 'Корректировки', roles: ['STOREKEEPER', 'ADMIN'] },
  { path: '/write-offs', label: 'Списания', roles: ['STOREKEEPER', 'ADMIN'] },
  { path: '/reports', label: 'Отчёты', roles: ['STOREKEEPER', 'ADMIN'] },
  { path: '/admin/users', label: 'Пользователи', roles: ['ADMIN'] },
  { path: '/admin/catalog', label: 'Каталог', roles: ['ADMIN'] },
  { path: '/admin/warehouses', label: 'Склады и секции', roles: ['ADMIN'] },
  { path: '/admin/rules', label: 'Правила размещения', roles: ['ADMIN'] },
  { path: '/audit', label: 'Аудит', roles: ['ADMIN'] },
];