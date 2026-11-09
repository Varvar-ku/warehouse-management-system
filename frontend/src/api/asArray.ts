/** Некоторые списки бэкенда приходят массивом, некоторые страницей {content}. Приводим к массиву. */
export function asArray<T>(data: unknown): T[] {
  if (Array.isArray(data)) return data as T[];
  if (data && typeof data === 'object') {
    const o = data as { content?: unknown; items?: unknown };
    if (Array.isArray(o.content)) return o.content as T[];
    if (Array.isArray(o.items)) return o.items as T[];
  }
  return [];
}
