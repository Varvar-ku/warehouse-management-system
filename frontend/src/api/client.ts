import { uuid } from '../lib/uuid';
import { ApiError, type ApiErrorBody } from './errors';

const BASE: string = import.meta.env.VITE_API_BASE ?? '/api/v1';

let token: string | null = null;
let onUnauthorized: () => void = () => {};
let refresher: (() => Promise<string | null>) | null = null;
let refreshing: Promise<string | null> | null = null;

export const setToken = (t: string | null) => {
  token = t;
};
export const setUnauthorizedHandler = (h: () => void) => {
  onUnauthorized = h;
};
/**
 * Обновление access-токена (его регистрирует AuthContext). Контракт: возвращает новый токен; null, если сервер
 * отклонил refresh (сессия закончена); бросает ошибку при временном сбое (сеть, 5xx): сессию завершать нельзя.
 */
export const setRefresher = (fn: (() => Promise<string | null>) | null) => {
  refresher = fn;
};

/** Одно обновление на всех: параллельные запросы с просроченным токеном ждут один и тот же вызов (single-flight). */
export function refreshToken(): Promise<string | null> {
  if (!refresher) return Promise.resolve(null);
  if (!refreshing) {
    refreshing = refresher().finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

type QueryValue = string | number | boolean | string[] | null | undefined;

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE';
  body?: unknown;
  query?: Record<string, QueryValue>;
  /** true: новый Idempotency-Key; строка: ключ повторяемой операции (при повторе после сбоя). */
  idempotencyKey?: boolean | string;
  signal?: AbortSignal;
  /**
   * Запрос без Authorization и без автоматического обновления токена (refresh, logout).
   * Просроченный токен на публичном пути дал бы 401, поэтому токен туда не отправляется.
   */
  anonymous?: boolean;
}

function toApiError(res: Response, text: string, traceId: string): ApiError {
  let body: ApiErrorBody = {};
  try {
    body = text ? JSON.parse(text) : {};
  } catch {
    /* не JSON, например ответ прокси */
  }
  return new ApiError(
    res.status,
    body.code ?? `HTTP_${res.status}`,
    body.message ?? (res.status >= 500 ? 'Сервер временно недоступен' : 'Запрос отклонён'),
    body.details ?? [],
    body.trace_id ?? res.headers.get('X-Trace-Id') ?? traceId,
  );
}

export async function api<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const url = new URL(BASE + path, window.location.origin);
  for (const [k, v] of Object.entries(opts.query ?? {})) {
    if (v === undefined || v === null || v === '') continue;
    url.searchParams.set(k, Array.isArray(v) ? v.join(',') : String(v));
  }

  const traceId = uuid();
  // ключ создаётся один раз: повтор после обновления токена уходит с тем же ключом
  const idempotencyKey = opts.idempotencyKey
    ? opts.idempotencyKey === true
      ? uuid()
      : opts.idempotencyKey
    : undefined;
  let retried = false;

  for (;;) {
    const sentToken = opts.anonymous ? null : token;
    const headers: Record<string, string> = { Accept: 'application/json', 'X-Trace-Id': traceId };
    if (sentToken) headers.Authorization = `Bearer ${sentToken}`;
    if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
    if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

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

    const error = toApiError(res, text, traceId);
    // UNAUTHORIZED: токен просрочен или недействителен. Неверный пароль приходит с кодом INVALID_CREDENTIALS.
    if (res.status === 401 && error.code === 'UNAUTHORIZED' && !opts.anonymous) {
      if (!retried) {
        retried = true;
        if (token && token !== sentToken) continue; // токен уже обновил параллельный запрос
        let fresh: string | null = null;
        try {
          fresh = await refreshToken();
        } catch {
          throw error; // временный сбой обновления: сессию не завершаем, ошибка уходит вызывающему
        }
        if (fresh) continue;
      }
      onUnauthorized();
    }
    throw error;
  }
}
