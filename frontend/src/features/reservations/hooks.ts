import { useQuery } from '@tanstack/react-query';
import type { RequestResponse } from '../requests/types';
import { checkAvailability } from './api';

/** Доступность по позициям заявки (запрошено / доступно / не хватает). Считается по запрошенному количеству. */
export function useAvailability(r: RequestResponse, enabled: boolean) {
  const items = r.items
    .filter((i) => i.requestedQuantity > 0)
    .map((i) => ({ productId: i.productId, quantity: i.requestedQuantity }));
  return useQuery({
    queryKey: ['reservations', 'availability', r.id, r.status],
    queryFn: () => checkAvailability(r.warehouseId, items),
    enabled: enabled && items.length > 0,
  });
}
