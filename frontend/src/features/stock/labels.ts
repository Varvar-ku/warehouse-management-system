import type { MovementType } from './api';

export const MOVEMENT_TYPE: Record<MovementType, { label: string; color: string }> = {
  RECEIPT: { label: 'Поступление', color: 'green' },
  ISSUE: { label: 'Выдача', color: 'blue' },
  WRITE_OFF: { label: 'Списание', color: 'red' },
  TRANSFER: { label: 'Перемещение', color: 'purple' },
  ADJUSTMENT: { label: 'Корректировка', color: 'orange' },
  RESERVATION: { label: 'Резерв', color: 'default' },
};

export const MOVEMENT_TYPE_OPTIONS = (Object.keys(MOVEMENT_TYPE) as MovementType[]).map((t) => ({
  value: t,
  label: MOVEMENT_TYPE[t].label,
}));
