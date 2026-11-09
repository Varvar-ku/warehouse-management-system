import type { WriteOffReason, WriteOffStatus } from './types';

export const WRITE_OFF_STATUS: Record<WriteOffStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  CONFIRMED: { label: 'Проведено', color: 'success' },
  CANCELLED: { label: 'Отменено', color: 'default' },
};

export const WRITE_OFF_REASON: Record<WriteOffReason, string> = {
  DAMAGED: 'Повреждение',
  EXPIRED: 'Истёк срок годности',
  LOST: 'Утеря',
  SCRAP: 'Брак',
  OTHER: 'Другое',
};

export const WRITE_OFF_STATUS_OPTIONS = (Object.keys(WRITE_OFF_STATUS) as WriteOffStatus[]).map((s) => ({
  value: s,
  label: WRITE_OFF_STATUS[s].label,
}));

export const WRITE_OFF_REASON_OPTIONS = (Object.keys(WRITE_OFF_REASON) as WriteOffReason[]).map((r) => ({
  value: r,
  label: WRITE_OFF_REASON[r],
}));
