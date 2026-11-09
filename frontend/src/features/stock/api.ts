import { api } from '../../api/client';
import type { Page } from '../../api/types';

export interface StockRow {
  id: string;
  warehouseId: string;
  productId: string;
  productSku: string;
  productName: string;
  sectionId: string;
  sectionCode: string;
  batchId: string;
  batchNumber: string | null;
  unitCode: string;
  quantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  /** Остаток на проверке после недостачи при сборе: не резервируется, не списывается, пока кладовщик не решит. */
  onHold: boolean;
}

export type MovementType = 'RECEIPT' | 'ISSUE' | 'WRITE_OFF' | 'TRANSFER' | 'ADJUSTMENT' | 'RESERVATION';

export interface Movement {
  id: string;
  movementType: MovementType;
  warehouseId: string;
  productId: string;
  batchId: string | null;
  fromSectionId: string | null;
  fromSectionCode: string | null;
  toSectionId: string | null;
  toSectionCode: string | null;
  quantity: number;
  unitCode: string;
  documentType: string | null;
  documentId: string | null;
  performedBy: string | null;
  performedAt: string;
  comment: string | null;
}

export interface StockFilters {
  warehouseId?: string;
  sectionId?: string;
  productId?: string;
  onlyAvailable?: boolean;
  includeZero?: boolean;
  page: number;
  size: number;
}

export interface MovementFilters {
  warehouseId?: string;
  productId?: string;
  sectionId?: string;
  type?: MovementType;
  documentType?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export const listStock = (f: StockFilters) => api<Page<StockRow>>('/stock', { query: { ...f } });

export const listMovements = (f: MovementFilters) => api<Page<Movement>>('/stock/movements', { query: { ...f } });
