import { api } from '../../../api/client';
import type { Page } from '../../../api/types';
import type { Warehouse } from '../../reference/types';
import type { CreateSectionBody, Section, SectionBody, SectionStatus } from './types';

export const createWarehouse = (body: { code: string; name: string; address?: string }) =>
  api<Warehouse>('/warehouses', { method: 'POST', body });

export const updateWarehouse = (id: string, body: { name?: string; address?: string; active?: boolean }) =>
  api<Warehouse>(`/warehouses/${id}`, { method: 'PATCH', body });

export interface SectionFilters {
  warehouseId?: string;
  status?: SectionStatus;
  q?: string;
  zone?: string;
  includeArchived?: boolean;
  page: number;
  size: number;
}

export const searchAdminSections = (f: SectionFilters) => api<Page<Section>>('/sections', { query: { ...f } });
export const createSection = (body: CreateSectionBody) => api<Section>('/sections', { method: 'POST', body });
export const updateSection = (id: string, body: SectionBody) => api<Section>(`/sections/${id}`, { method: 'PATCH', body });
