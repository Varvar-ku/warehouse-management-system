import { api } from '../../api/client';
import type { Page } from '../../api/types';

export type IssueStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';

export interface IssueItem {
  id: string;
  requestItemId: string;
  productId: string;
  productSku: string;
  productName: string;
  stockId: string;
  sectionCode: string;
  batchNumber: string | null;
  unitCode: string;
  quantity: number;
}

export interface Issue {
  id: string;
  number: string;
  warehouseId: string;
  requestId: string;
  issuedTo: string;
  issuedBy: string;
  status: IssueStatus;
  createdAt: string;
  issuedAt: string | null;
  comment: string | null;
  items: IssueItem[];
}

export interface IssueFilters {
  status?: IssueStatus;
  requestId?: string;
  page: number;
  size: number;
}

/** Выдача оформляется из заявки; здесь документы только просматриваются. */
export const listIssues = (f: IssueFilters) => api<Page<Issue>>('/issues', { query: { ...f } });
