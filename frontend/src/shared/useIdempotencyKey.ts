import { useRef } from 'react';
import { uuid } from '../lib/uuid';

/**
 * Ключ идемпотентности для одной логической операции. Повтор после сетевой ошибки уходит с тем же ключом,
 * а после успеха reset() выдаёт новый, чтобы следующая операция не приняла старый ответ.
 */
export function useIdempotencyKey() {
  const key = useRef(uuid());
  const body = useRef<string | null>(null);
  const reset = () => {
    body.current = null;
    return (key.current = uuid());
  };
  return {
    get: () => key.current,
    /**
     * Ключ, привязанный к содержимому запроса. Бэкенд сверяет отпечаток запроса: тот же ключ с другим телом даёт
     * 422 IDEMPOTENCY_KEY_REUSED. Если пользователь после сбоя изменил данные, выдаётся новый ключ.
     */
    forBody: (value: unknown) => {
      const s = JSON.stringify(value);
      if (body.current !== null && body.current !== s) key.current = uuid();
      body.current = s;
      return key.current;
    },
    reset,
  };
}
