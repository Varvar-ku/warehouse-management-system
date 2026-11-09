import type { ReportColumn, Row } from './types';

const cell = (v: string | number | null) => {
  const s = v === null || v === undefined ? '' : String(v);
  // Защита от формул: ячейка, начинающаяся с = + - @, в Excel исполнилась бы как формула
  const safe = /^[=+\-@\t\r]/.test(s) && !/^-?\d+([.,]\d+)?$/.test(s) ? `'${s}` : s;
  return /[";\r\n]/.test(safe) ? `"${safe.replace(/"/g, '""')}"` : safe;
};

/** CSV для русского Excel: разделитель «;», BOM в начале, CRLF между строками. */
export function toCsv(columns: ReportColumn[], rows: Row[]): string {
  const head = columns.map((c) => cell(c.title)).join(';');
  const body = rows.map((r) => columns.map((c) => cell((c.raw ?? c.value)(r))).join(';'));
  return '\uFEFF' + [head, ...body].join('\r\n');
}

export function downloadCsv(filename: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
