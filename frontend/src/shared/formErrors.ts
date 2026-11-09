import type { FormInstance } from 'antd';
import { ApiError } from '../api/errors';

/**
 * Раскладывает ошибки валидации бэкенда («items[0].quantity: ...») по полям формы.
 * Имена полей бэкенда, которые называются иначе в форме, переименовываются через `rename`.
 */
export function applyFieldErrors(form: FormInstance, error: unknown, rename: Record<string, string> = {}) {
  if (!(error instanceof ApiError)) return;
  const fields = Object.entries(error.fieldErrors).map(([path, errors]) => ({
    name: path
      .split(/[.[\]]+/)
      .filter(Boolean)
      .map((part) => (/^\d+$/.test(part) ? Number(part) : (rename[part] ?? part))),
    errors,
  }));
  if (fields.length > 0) form.setFields(fields);
}
