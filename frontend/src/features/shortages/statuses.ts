import type { ShortageResolution, ShortageSource, ShortageStatus } from './types';

export const SHORTAGE_STATUS: Record<ShortageStatus, { label: string; color: string }> = {
  OPEN: { label: 'Открыта', color: 'orange' },
  RESOLVED: { label: 'Закрыта', color: 'default' },
};

export const SHORTAGE_RESOLUTION: Record<ShortageResolution, string> = {
  FOUND: 'Найдено',
  WRITTEN_OFF: 'Списано корректировкой',
};

export const SHORTAGE_SOURCE: Record<ShortageSource, string> = {
  ISSUE: 'Выдача',
  CANCELLATION: 'Отмена заявки',
};

export const SHORTAGE_STATUS_OPTIONS = (Object.keys(SHORTAGE_STATUS) as ShortageStatus[]).map((s) => ({
  value: s,
  label: SHORTAGE_STATUS[s].label,
}));
