import type { AdjustmentStatus } from './types';

export const ADJUSTMENT_STATUS: Record<AdjustmentStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  CONFIRMED: { label: 'Проведено', color: 'success' },
  CANCELLED: { label: 'Отменено', color: 'default' },
};

export const ADJUSTMENT_STATUS_OPTIONS = (Object.keys(ADJUSTMENT_STATUS) as AdjustmentStatus[]).map((s) => ({
  value: s,
  label: ADJUSTMENT_STATUS[s].label,
}));
