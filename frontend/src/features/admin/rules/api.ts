import { api } from '../../../api/client';
import { asArray } from '../../../api/asArray';
import type { Page } from '../../../api/types';
import type { CreateRuleBody, PlacementRule, RuleVersion, UpdateRuleBody } from './types';

export interface RuleFilters {
  warehouseId?: string;
  active?: boolean;
  fallback?: boolean;
  page: number;
  size: number;
}

export const listRules = (f: RuleFilters) => api<Page<PlacementRule>>('/placement-rules', { query: { ...f } });

/** Склад правила определяется складом целевой секции, отдельного поля warehouseId в запросе нет. */
export const createRule = (body: CreateRuleBody) => api<PlacementRule>('/placement-rules', { method: 'POST', body });

export const updateRule = (id: string, body: UpdateRuleBody) =>
  api<PlacementRule>(`/placement-rules/${id}`, { method: 'PATCH', body });

export const listRuleVersions = async (id: string) =>
  asArray<RuleVersion>(await api<unknown>(`/placement-rules/${id}/versions`));
