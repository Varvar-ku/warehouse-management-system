import { useQuery } from '@tanstack/react-query';
import { listAttributeDefinitions, listProductTypes, listUnits } from './api';

/** Справочники меняются редко: кэш на 5 минут. После правки вызывающий код инвалидирует ключ ['catalog', ...]. */
export const useProductTypes = () =>
  useQuery({ queryKey: ['catalog', 'types'], queryFn: () => listProductTypes(true), staleTime: 5 * 60_000 });

export const useUnits = () => useQuery({ queryKey: ['catalog', 'units'], queryFn: listUnits, staleTime: 5 * 60_000 });

export const useAttributeDefinitions = () =>
  useQuery({ queryKey: ['catalog', 'attributes'], queryFn: listAttributeDefinitions, staleTime: 5 * 60_000 });
