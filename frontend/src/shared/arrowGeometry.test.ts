import { describe, expect, it } from 'vitest';
import { pickNext, type Box } from './arrowGeometry';

const box = (left: number, top: number, w = 80, h = 32): Box => ({ left, top, right: left + w, bottom: top + h });

describe('pickNext', () => {
  const row = [box(0, 0), box(100, 0), box(200, 0)];

  it('→ берёт ближайшую справа в том же ряду', () => {
    // текущая вторая (100,0): справа остаётся третья
    expect(pickNext(row[1], [row[0], row[2]], 'right')).toBe(1);
  });

  it('← берёт ближайшую слева', () => {
    expect(pickNext(row[1], [row[0], row[2]], 'left')).toBe(0);
  });

  it('на краю ряда → идёт на начало следующего ряда', () => {
    const second = [box(0, 50), box(100, 50)];
    expect(pickNext(row[2], [row[0], row[1], ...second], 'right')).toBe(2);
  });

  it('на краю ряда ← идёт на конец предыдущего ряда', () => {
    const prev = [box(0, 0), box(100, 0)];
    expect(pickNext(box(0, 50), [...prev, box(100, 50)], 'left')).toBe(1);
  });

  it('↓ идёт к кнопке под текущей, а не к соседней в ряду', () => {
    const cur = box(0, 0);
    const right = box(100, 0);
    const below = box(0, 60);
    expect(pickNext(cur, [right, below], 'down')).toBe(1);
  });

  it('↑ без кнопок выше возвращает -1', () => {
    expect(pickNext(box(0, 0), [box(100, 0)], 'up')).toBe(-1);
  });

  it('→ в одиночной кнопке возвращает -1', () => {
    expect(pickNext(box(0, 0), [], 'right')).toBe(-1);
  });
});
