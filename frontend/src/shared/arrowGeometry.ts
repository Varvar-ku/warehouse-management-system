export type Dir = 'left' | 'right' | 'up' | 'down';

export interface Box {
  left: number;
  right: number;
  top: number;
  bottom: number;
}

const cx = (b: Box) => (b.left + b.right) / 2;
const cy = (b: Box) => (b.top + b.bottom) / 2;
const overlap = (a0: number, a1: number, b0: number, b1: number) => Math.min(a1, b1) - Math.max(a0, b0) > 0;

/**
 * Следующий элемент в направлении стрелки. Возвращает индекс в boxes или -1.
 * boxes идут в порядке DOM; cur не входит в список.
 *
 * 1. Сначала элементы строго в направлении, лежащие «на одной линии» (перекрытие по поперечной оси);
 *    из них берётся ближайший. Так ←/→ идут по ряду кнопок, ↑/↓ по колонке.
 * 2. Если таких нет: для ←/→ переход по порядку DOM (с конца ряда на начало следующего),
 *    для ↑/↓ ближайший элемент в этом направлении без требования «одной линии».
 */
export function pickNext(cur: Box, boxes: Box[], dir: Dir): number {
  const horizontal = dir === 'left' || dir === 'right';
  const sign = dir === 'right' || dir === 'down' ? 1 : -1;
  const primary = (b: Box) => (horizontal ? cx(b) - cx(cur) : cy(b) - cy(cur)) * sign;
  const aligned = (b: Box) =>
    horizontal ? overlap(cur.top, cur.bottom, b.top, b.bottom) : overlap(cur.left, cur.right, b.left, b.right);
  const side = (b: Box) => (horizontal ? Math.abs(cy(b) - cy(cur)) : Math.abs(cx(b) - cx(cur)));

  let best = -1;
  let bestScore = Infinity;
  boxes.forEach((b, i) => {
    const p = primary(b);
    if (p <= 1 || !aligned(b)) return;
    const score = p + side(b) * 0.5;
    if (score < bestScore) {
      bestScore = score;
      best = i;
    }
  });
  if (best >= 0) return best;

  if (horizontal) {
    // порядок DOM: следующий для →, предыдущий для ←; позицию текущего находим по расположению относительно списка
    const before = boxes.map((b, i) => ({ b, i })).filter(({ b }) => cy(b) < cy(cur) - 1 || (Math.abs(cy(b) - cy(cur)) <= 1 && cx(b) < cx(cur)));
    const after = boxes.map((b, i) => ({ b, i })).filter(({ b }) => cy(b) > cy(cur) + 1 || (Math.abs(cy(b) - cy(cur)) <= 1 && cx(b) > cx(cur)));
    const pick = sign > 0 ? after[0] : before[before.length - 1];
    return pick ? pick.i : -1;
  }

  boxes.forEach((b, i) => {
    const p = primary(b);
    if (p <= 1) return;
    const score = p + side(b) * 2;
    if (score < bestScore) {
      bestScore = score;
      best = i;
    }
  });
  return best;
}
