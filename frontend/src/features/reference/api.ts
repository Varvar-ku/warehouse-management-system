import { api } from '../../api/client';
import type { Page } from '../../api/types';
import type { Product, Warehouse } from './types';

export const listWarehouses = () => api<Warehouse[]>('/warehouses');

export const searchProducts = (q: string, size = 20) => api<Page<Product>>('/products', { query: { q, size } });

export const fetchProducts = (ids: string[]) => api<Product[]>('/products/batch', { query: { ids } });
