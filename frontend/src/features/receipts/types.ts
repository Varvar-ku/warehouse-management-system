/* Типы по InventoryDtos.java (inventory-service). */

export type ReceiptStatus = 'DRAFT' | 'RECEIVING' | 'PLACEMENT' | 'DONE' | 'CANCELLED';

/** ReceiptItem.PlacementStatus. REJECTED: позиция закрыта решением кладовщика без прихода (брак, потеря, недостача). */
export type ItemPlacementStatus = 'NEW' | 'PENDING' | 'PLACED' | 'DISCREPANCY' | 'REJECTED';

export interface RecommendationInfo {
  sectionId: string | null;
  sectionCode: string | null;
  strategy: string | null;
  ruleId: string | null;
  ruleVersion: number | null;
  ruleName: string | null;
  calculatedAt: string | null;
  notes: string[] | null;
}

export interface ReceiptItem {
  id: string;
  lineNo: number;
  productId: string;
  productSku: string;
  productName: string;
  unitCode: string;
  quantity: number;
  batchNumber: string | null;
  expiryAt: string | null;
  comment: string | null;
  recommendation: RecommendationInfo | null;
  targetSectionId: string | null;
  targetSectionCode: string | null;
  targetDecision: string | null;
  placementStatus: string;
  placedSectionId: string | null;
  placedSectionCode: string | null;
  placedQuantity: number | null;
  discrepancyComment: string | null;
  batchId: string | null;
}

export interface Receipt {
  id: string;
  number: string;
  warehouseId: string;
  supplierName: string;
  externalDocumentNumber: string | null;
  status: ReceiptStatus;
  receivedAt: string;
  createdBy: string;
  createdAt: string;
  confirmedAt: string | null;
  completedAt: string | null;
  cancelReason: string | null;
  items: ReceiptItem[];
}

export interface ReceiptSummary {
  id: string;
  number: string;
  warehouseId: string;
  supplierName: string;
  externalDocumentNumber: string | null;
  status: ReceiptStatus;
  receivedAt: string;
  itemsCount: number;
}

export interface NumericValue {
  value: number;
  unitCode?: string;
}

export interface Characteristics {
  material?: string;
  numeric?: Record<string, NumericValue>;
}

export interface ReceiptItemInput {
  productId: string;
  quantity: number;
  batchNumber?: string;
  expiryAt?: string;
  comment?: string;
  characteristics?: Characteristics;
}

export interface CreateReceiptBody {
  warehouseId: string;
  supplierName: string;
  externalDocumentNumber?: string;
  items: ReceiptItemInput[];
}

/** PATCH только для DRAFT: переданные поля заменяются, items заменяется целиком. */
export interface UpdateReceiptBody {
  supplierName?: string;
  externalDocumentNumber?: string;
  receivedAt?: string;
  items?: ReceiptItemInput[];
}

export interface Candidate {
  sectionId: string;
  sectionCode: string;
  freeCapacity: number | null;
}

export interface ItemRecommendation {
  itemId: string;
  lineNo: number;
  status: string;
  strategy: string | null;
  sectionId: string | null;
  sectionCode: string | null;
  ruleId: string | null;
  ruleVersion: number | null;
  ruleName: string | null;
  calculatedAt: string | null;
  requiresStorekeeperAction: boolean;
  notes: string[] | null;
  candidates: Candidate[] | null;
}

export interface ItemDecision {
  itemId: string;
  targetSectionId: string;
}
