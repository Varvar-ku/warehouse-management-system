export interface ApiErrorBody {
  code?: string;
  message?: string;
  details?: string[];
  trace_id?: string | null;
}

/** Ошибка API в едином формате бэкенда: code / message / details / trace_id. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly details: string[] = [],
    readonly traceId: string | null = null,
  ) {
    super(message);
    this.name = 'ApiError';
  }

  /** Ошибки валидации приходят как «поле: сообщение»; раскладываем по полям формы. */
  get fieldErrors(): Record<string, string[]> {
    const out: Record<string, string[]> = {};
    for (const d of this.details) {
      const i = d.indexOf(': ');
      if (i > 0) (out[d.slice(0, i)] ??= []).push(d.slice(i + 2));
    }
    return out;
  }
}

export function describeError(e: unknown): { message: string; traceId: string | null } {
  if (e instanceof ApiError) {
    const extra = e.details.length ? ` (${e.details.join('; ')})` : '';
    return { message: e.message + extra, traceId: e.traceId };
  }
  return { message: 'Непредвиденная ошибка', traceId: null };
}
