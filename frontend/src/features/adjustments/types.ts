export type AdjustmentStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';

export interface AdjustmentItem {
  id: string;
  shortageId: string;
  shortageNumber: string;
  stockId: string;
  productId: string;
  productSku: string;
  productName: string;
  sectionCode: string;
  batchNumber: string | null;
  unitCode: string;
  quantity: number;
}

/** Документ корректировки: уменьшает остаток на количество недостачи по каждой включённой позиции. */
export interface Adjustment {
  id: string;
  number: string;
  warehouseId: string;
  comment: string;
  status: AdjustmentStatus;
  createdBy: string;
  createdAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
  confirmedBy: string | null;
  confirmedAt: string | null;
  cancelReason: string | null;
  items: AdjustmentItem[];
}

export interface CreateAdjustmentBody {
  warehouseId: string;
  comment: string;
  items: { shortageId: string }[];
}
