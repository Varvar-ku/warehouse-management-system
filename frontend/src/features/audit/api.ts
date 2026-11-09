import { api } from '../../api/client';

export interface AuditEvent {
  id: number;
  eventId: string;
  schemaVersion: number;
  service: string;
  action: string;
  entityType: string;
  entityId: string;
  actorId: string | null;
  actorRole: string | null;
  occurredAt: string;
  receivedAt: string;
  traceId: string | null;
  details: unknown;
}

/** Страница аудита отличается от общей: items и totalItems вместо content и totalElements. */
export interface AuditPage {
  items: AuditEvent[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface AuditFilters {
  entityType?: string;
  entityId?: string;
  actorId?: string;
  /** Точное действие или префикс со звёздочкой на конце, например request.* */
  action?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export const listAuditEvents = (f: AuditFilters) => api<AuditPage>('/audit/events', { query: { ...f } });
