import { useMemo } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listUsersByIds, listWorkers } from './api';

export function useWorkers(enabled = true) {
  return useQuery({ queryKey: ['directory', 'workers'], queryFn: listWorkers, enabled, staleTime: 60_000 });
}

/** Имена пользователей по id. USER этот справочник недоступен: вызывайте с enabled = false. */
export function useUserNames(ids: (string | null | undefined)[], enabled = true): Map<string, string> {
  const key = [...new Set(ids.filter((x): x is string => !!x))].sort().join(',');
  const q = useQuery({
    queryKey: ['directory', 'users', key],
    queryFn: () => listUsersByIds(key.split(',')),
    enabled: enabled && key !== '',
    staleTime: 5 * 60_000,
    placeholderData: keepPreviousData,
  });
  return useMemo(() => new Map((q.data ?? []).map((u) => [u.id, u.fullName])), [q.data]);
}

export const nameOf = (names: Map<string, string>, id?: string | null) =>
  id ? (names.get(id) ?? 'Сотрудник') : '—';
