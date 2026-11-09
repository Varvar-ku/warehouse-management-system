import { useQuery } from '@tanstack/react-query';
import { listWarehouses } from './api';

/** Список складов меняется редко, поэтому кэшируется на 5 минут. */
export function useWarehouses() {
  return useQuery({ queryKey: ['warehouses'], queryFn: listWarehouses, staleTime: 5 * 60_000 });
}
