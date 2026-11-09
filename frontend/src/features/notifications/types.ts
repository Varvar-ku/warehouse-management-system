export type NotificationType =
  | 'REQUEST_APPROVED'
  | 'REQUEST_PARTIALLY_AVAILABLE'
  | 'REQUEST_RESERVED'
  | 'REQUEST_RESERVED_PARTIAL'
  | 'REQUEST_READY_FOR_ISSUE'
  | 'REQUEST_ISSUED'
  | 'REQUEST_REJECTED'
  | 'REQUEST_CANCELLED';

export interface NotificationItem {
  id: string;
  type: NotificationType;
  title: string;
  message: string;
  requestId: string | null;
  createdAt: string;
  readAt: string | null;
  read: boolean;
}
