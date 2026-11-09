import { useMemo } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { fetchProducts } from './api';

/** Названия товаров по id для таблиц, где бэкенд отдаёт только productId (например, журнал движений). */
export function useProductNames(ids: (string | null | undefined)[]): Map<string, string> {
  const key = [...new Set(ids.filter((x): x is string => !!x))].sort().join(',');
  const q = useQuery({
    queryKey: ['products', 'names', key],
    queryFn: () => fetchProducts(key.split(',')),
    enabled: key !== '',
    staleTime: 5 * 60_000,
    placeholderData: keepPreviousData,
  });
  return useMemo(() => new Map((q.data ?? []).map((p) => [p.id, `${p.sku} — ${p.name}`])), [q.data]);
}
