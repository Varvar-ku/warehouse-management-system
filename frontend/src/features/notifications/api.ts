import { api } from '../../api/client';
import type { Page } from '../../api/types';
import type { NotificationItem } from './types';

export const fetchUnreadCount = () => api<{ unread: number }>('/notifications/unread-count');

export const listNotifications = (page = 0, size = 10) =>
  api<Page<NotificationItem>>('/notifications', { query: { page, size } });

export const markRead = (id: string) => api<NotificationItem>(`/notifications/${id}/read`, { method: 'POST' });

export const markAllRead = () => api<{ updated: number }>('/notifications/read-all', { method: 'POST' });
