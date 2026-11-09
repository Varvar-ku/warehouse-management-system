/** Страница отчёта (report-service ReportPage): форма совпадает с общей страницей. */
export interface ReportPage<T = Row> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Строки разных отчётов различаются, поэтому в общей таблице они читаются по ключам. */
export type Row = Record<string, unknown>;

export interface ReportColumn {
  title: string;
  /** Текст для таблицы. */
  value: (r: Row) => string;
  /** Значение для CSV: числа без пробелов-разделителей. По умолчанию равно value. */
  raw?: (r: Row) => string | number | null;
  align?: 'left' | 'right';
}

export type FilterDef =
  | { kind: 'range' }
  | { kind: 'date'; name: string; label: string }
  | { kind: 'text'; name: string; label: string; width?: number }
  | { kind: 'uuid'; name: string; label: string }
  | { kind: 'select'; name: string; label: string; options: { value: string; label: string }[] }
  | { kind: 'bool'; name: string; label: string }
  | { kind: 'warehouse'; name: string }
  | { kind: 'worker'; name: string };

export interface ReportDef {
  key: string;
  title: string;
  group: string;
  path: string;
  description?: string;
  filters: FilterDef[];
  columns: ReportColumn[];
}
