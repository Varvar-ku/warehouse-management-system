import type { RequestStatus } from '../../shared/statuses';

export interface RequestItem {
  id: string;
  lineNo: number;
  productId: string;
  productSku: string;
  productName: string;
  unitCode: string;
  requestedQuantity: number;
  approvedQuantity: number | null;
  reservedQuantity: number | null;
  issuedQuantity: number | null;
  unavailableQuantity: number | null;
  comment: string | null;
}

export interface RequestResponse {
  id: string;
  number: string;
  requesterId: string;
  warehouseId: string;
  status: RequestStatus;
  priority: number;
  comment: string | null;
  statusReason: string | null;
  createdAt: string;
  submittedAt: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  closedAt: string | null;
  issueNumber: string | null;
  items: RequestItem[];
}

export interface RequestSummary {
  id: string;
  number: string;
  requesterId: string;
  warehouseId: string;
  status: RequestStatus;
  priority: number;
  createdAt: string;
  submittedAt: string | null;
  itemsCount: number;
}

export interface HistoryEntry {
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus;
  actorId: string | null;
  comment: string | null;
  createdAt: string;
}

export interface ItemInput {
  productId: string;
  quantity: number;
  comment?: string;
}

export interface CreateRequestBody {
  warehouseId: string;
  priority: number;
  comment?: string;
  items: ItemInput[];
}

/** PATCH только для черновика. Если передан items, позиции заменяются целиком. */
export interface UpdateRequestBody {
  priority?: number;
  comment?: string;
  items?: ItemInput[];
}

export interface RequestFilters {
  status?: RequestStatus;
  warehouseId?: string;
  q?: string;
  page: number;
  size: number;
}
