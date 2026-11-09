import { uuid } from '../lib/uuid';
import { ApiError, type ApiErrorBody } from './errors';

const BASE: string = import.meta.env.VITE_API_BASE ?? '/api/v1';

let token: string | null = null;
let onUnauthorized: () => void = () => {};

export const setToken = (t: string | null) => {
  token = t;
};
export const setUnauthorizedHandler = (h: () => void) => {
  onUnauthorized = h;
};

type QueryValue = string | number | boolean | string[] | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue>;
  /** true: новый Idempotency-Key; строка: ключ повторяемой операции (при повторе после сбоя). */
  idempotencyKey?: boolean | string;
  signal?: AbortSignal;
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const url = new URL(BASE + path, window.location.origin);
  for (const [k, v] of Object.entries(opts.query ?? {})) {
    if (v === undefined || v === null || v === '') continue;
    url.searchParams.set(k, Array.isArray(v) ? v.join(',') : String(v));
  }

  const traceId = uuid();
  const headers: Record<string, string> = { Accept: 'application/json', 'X-Trace-Id': traceId };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (opts.idempotencyKey) {
    headers['Idempotency-Key'] = opts.idempotencyKey === true ? uuid() : opts.idempotencyKey;
  }

  let res: Response;
  try {
    res = await fetch(url, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      signal: opts.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e;
    throw new ApiError(0, 'NETWORK_ERROR', 'Сервер недоступен. Проверьте подключение и повторите.', [], traceId);
  }

  const text = await res.text();
  if (res.ok) return (text ? JSON.parse(text) : undefined) as T;

  let body: ApiErrorBody = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    /* не JSON, например ответ прокси */
  }
  const error = new ApiError(
    res.status,
    body.code ?? `HTTP_${res.status}`,
    body.message ?? (res.status >= 500 ? 'Сервер временно недоступен' : 'Запрос отклонён'),
    body.details ?? [],
    body.trace_id ?? res.headers.get('X-Trace-Id') ?? traceId,
  );
  // UNAUTHORIZED: токен просрочен или недействителен. Неверный пароль приходит с кодом INVALID_CREDENTIALS.
  if (res.status === 401 && error.code === 'UNAUTHORIZED') onUnauthorized();
  throw error;
}
