export type WriteOffStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED';
export type WriteOffReason = 'DAMAGED' | 'EXPIRED' | 'LOST' | 'SCRAP' | 'OTHER';

export interface WriteOffItem {
  id: string;
  stockId: string;
  productId: string;
  productSku: string;
  productName: string;
  sectionCode: string;
  batchNumber: string | null;
  unitCode: string;
  quantity: number;
}

export interface WriteOff {
  id: string;
  number: string;
  warehouseId: string;
  reasonCode: WriteOffReason;
  comment: string | null;
  status: WriteOffStatus;
  approvalRequired: boolean;
  approvedBy: string | null;
  approvedAt: string | null;
  createdBy: string;
  createdAt: string;
  confirmedBy: string | null;
  confirmedAt: string | null;
  items: WriteOffItem[];
}

export interface CreateWriteOffBody {
  warehouseId: string;
  reasonCode: WriteOffReason;
  comment?: string;
  items: { stockId: string; quantity: number }[];
}
