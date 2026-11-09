import type { Dayjs } from 'dayjs';

/** Значение RangePicker в параметры from/to (ISO date-time; конец периода включает весь последний день). */
export function rangeToQuery(range: [Dayjs | null, Dayjs | null] | null | undefined, withTime: boolean) {
  const from = range?.[0];
  const to = range?.[1];
  return {
    from: from ? (withTime ? from : from.startOf('day')).toISOString() : undefined,
    to: to ? (withTime ? to : to.endOf('day')).toISOString() : undefined,
  };
}
