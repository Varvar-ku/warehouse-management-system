import type { ReceiptStatus } from './types';

export const RECEIPT_STATUS: Record<ReceiptStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  RECEIVING: { label: 'Принято, размещение не начато', color: 'blue' },
  PLACEMENT: { label: 'Размещение', color: 'processing' },
  DONE: { label: 'Завершено', color: 'success' },
  CANCELLED: { label: 'Отменено', color: 'default' },
};

export const RECEIPT_STATUS_OPTIONS = (Object.keys(RECEIPT_STATUS) as ReceiptStatus[]).map((s) => ({
  value: s,
  label: RECEIPT_STATUS[s].label,
}));

/** ReceiptItem.PlacementStatus */
export const ITEM_PLACEMENT_STATUS: Record<string, { label: string; color: string }> = {
  NEW: { label: 'Новая', color: 'default' },
  PENDING: { label: 'Ожидает размещения', color: 'blue' },
  PLACED: { label: 'Размещена', color: 'success' },
  DISCREPANCY: { label: 'Расхождение', color: 'orange' },
  REJECTED: { label: 'Отклонена', color: 'red' },
};

/** ReceiptItem.TargetDecision */
export const TARGET_DECISION: Record<string, string> = {
  RECOMMENDED: 'по рекомендации',
  ALTERNATIVE: 'выбрано кладовщиком',
  UNASSIGNED_ZONE: 'зона «Нераспределено»',
};

/** PlacementStrategy (warehouse-service): как выбрана секция. */
export const PLACEMENT_STRATEGY: Record<string, string> = {
  RULE: 'по правилу размещения',
  SECTION_PRIORITY: 'по приоритету секции',
  FALLBACK_RULE: 'по резервному правилу',
  UNASSIGNED_ZONE: 'зона «Нераспределено»',
};
