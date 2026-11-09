export const fmtDateTime = (iso?: string | null) => (iso ? new Date(iso).toLocaleString('ru-RU') : '—');

/** Единица количества во всём интерфейсе: товар учитывается в штуках, выбора единицы нет. */
export const PCS = 'шт';

const qtyFormat = new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 4 });
export const fmtQty = (v?: number | null) => (v === null || v === undefined ? '—' : qtyFormat.format(v));
