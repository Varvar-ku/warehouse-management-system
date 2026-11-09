import { api } from '../../api/client';
import { asArray } from '../../api/asArray';

export interface AvailabilityItem {
  productId: string;
  requested: number;
  available: number;
  shortage: number;
}

export interface Reservation {
  id: string;
  requestId: string;
  requestItemId: string;
  stockId: string;
  sectionId: string;
  sectionCode: string;
  batchId: string;
  batchNumber: string | null;
  productSku: string;
  quantity: number;
  consumedQuantity: number;
  releasedQuantity: number;
  status: string;
  createdAt: string;
  closedAt: string | null;
}

export const checkAvailability = async (
  warehouseId: string,
  items: { productId: string; quantity: number }[],
) =>
  asArray<AvailabilityItem>(
    await api<unknown>('/reservations/availability', { method: 'POST', body: { warehouseId, items } }),
  );

export const listReservations = async (requestId: string) =>
  asArray<Reservation>(await api<unknown>('/reservations', { query: { requestId } }));
