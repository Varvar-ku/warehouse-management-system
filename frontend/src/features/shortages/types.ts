export type ShortageStatus = 'OPEN' | 'RESOLVED';
export type ShortageResolution = 'FOUND' | 'WRITTEN_OFF';
/** ISSUE: обнаружена при выдаче; CANCELLATION: при снятии резерва отменённой заявки. */
export type ShortageSource = 'ISSUE' | 'CANCELLATION';

/** Недостача: строка остатка «на проверке» не резервируется, пока кладовщик не примет решение. */
export interface Shortage {
  id: string;
  number: string;
  warehouseId: string;
  productId: string;
  productSku: string;
  productName: string;
  stockId: string;
  sectionCode: string;
  batchNumber: string | null;
  unitCode: string;
  quantity: number;
  requestId: string | null;
  requestItemId: string | null;
  issueId: string | null;
  source: ShortageSource;
  status: ShortageStatus;
  resolution: ShortageResolution | null;
  resolutionComment: string | null;
  /** Документ корректировки, в который включена недостача (пока он не отменён). */
  adjustmentId: string | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  createdAt: string;
}
