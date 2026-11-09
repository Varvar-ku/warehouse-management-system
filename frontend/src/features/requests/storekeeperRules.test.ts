import { describe, expect, it } from 'vitest';
import { storekeeperActions } from './storekeeperRules';
import { USER_CANCELLABLE } from './rules';

describe('кнопки кладовщика по статусам заявки', () => {
  it('SUBMITTED: проверить, отклонить, отменить', () => {
    expect(storekeeperActions('SUBMITTED')).toEqual(['review', 'reject', 'cancel']);
  });

  it('отклонить можно из SUBMITTED, APPROVED и PARTIALLY_AVAILABLE', () => {
    for (const s of ['SUBMITTED', 'APPROVED', 'PARTIALLY_AVAILABLE'] as const) {
      expect(storekeeperActions(s)).toContain('reject');
    }
  });

  it('после резерва отклонить нельзя, остаётся отмена', () => {
    for (const s of ['RESERVED', 'RESERVED_PARTIAL', 'PICKING'] as const) {
      expect(storekeeperActions(s)).not.toContain('reject');
      expect(storekeeperActions(s)).toContain('cancel');
    }
  });

  it('повторная проверка доступна из APPROVED и PARTIALLY_AVAILABLE', () => {
    expect(storekeeperActions('APPROVED')).toContain('review');
    expect(storekeeperActions('PARTIALLY_AVAILABLE')).toContain('review');
  });

  it('устаревший UNDER_REVIEW не даёт действий', () => {
    expect(storekeeperActions('UNDER_REVIEW')).toEqual([]);
  });

  it('PICKED не отменяется', () => {
    expect(storekeeperActions('PICKED')).toEqual(['ready']);
  });

  it('READY_FOR_ISSUE: выдать, а если ничего не собрано, только отменить', () => {
    expect(storekeeperActions('READY_FOR_ISSUE', { nothingPicked: false })).toEqual(['issue']);
    expect(storekeeperActions('READY_FOR_ISSUE', { nothingPicked: true })).toEqual(['cancel']);
  });

  it('PICKING_WITH_SHORTAGE: готово к выдаче и отмена; без собранного только отмена', () => {
    expect(storekeeperActions('PICKING_WITH_SHORTAGE', { nothingPicked: false })).toEqual(['ready', 'cancel']);
    expect(storekeeperActions('PICKING_WITH_SHORTAGE', { nothingPicked: true })).toEqual(['cancel']);
  });

  it('REJECTED и CANCELLED закрываются', () => {
    expect(storekeeperActions('REJECTED')).toEqual(['close']);
    expect(storekeeperActions('CANCELLED')).toEqual(['close']);
  });
});

describe('отмена заявки пользователем', () => {
  it('только до резерва', () => {
    expect(USER_CANCELLABLE).toEqual(['SUBMITTED', 'APPROVED', 'PARTIALLY_AVAILABLE']);
  });
});
