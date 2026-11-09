import { describe, expect, it } from 'vitest';
import { toCsv } from './csv';
import type { ReportColumn } from './types';

const cols: ReportColumn[] = [
  { title: 'Товар', value: (r) => String(r.name) },
  { title: 'Количество', value: (r) => String(r.qty), raw: (r) => String(r.qty).replace('.', ',') },
];

describe('toCsv', () => {
  it('начинается с BOM, разделитель «;», строки через CRLF', () => {
    const csv = toCsv(cols, [{ name: 'Болт', qty: 1.5 }]);
    expect(csv.startsWith('\uFEFF')).toBe(true);
    expect(csv.split('\r\n')).toEqual(['\uFEFFТовар;Количество', 'Болт;1,5']);
  });

  it('экранирует кавычки, точку с запятой и переводы строк', () => {
    const csv = toCsv(cols, [{ name: 'Болт "М8"; оцинк.', qty: 1 }]);
    expect(csv).toContain('"Болт ""М8""; оцинк."');
  });

  it('защищает от формул в ячейках', () => {
    const csv = toCsv(cols, [{ name: '=SUM(A1:A2)', qty: 1 }]);
    expect(csv).toContain("'=SUM(A1:A2)");
  });

  it('не портит отрицательные числа', () => {
    const csv = toCsv(cols, [{ name: 'Расхождение', qty: -3 }]);
    expect(csv.split('\r\n')[1]).toBe('Расхождение;-3');
  });
});
