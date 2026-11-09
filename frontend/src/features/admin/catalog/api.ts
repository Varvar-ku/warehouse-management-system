import { api } from '../../../api/client';
import { asArray } from '../../../api/asArray';
import type { Page } from '../../../api/types';
import type {
  AttributeDataType,
  AttributeDefinition,
  CatalogProduct,
  CreateProductBody,
  ProductType,
  UpdateProductBody,
  Unit,
} from './types';

export interface ProductFilters {
  q?: string;
  productTypeId?: string;
  includeArchived?: boolean;
  page: number;
  size: number;
}

export const searchCatalogProducts = (f: ProductFilters) => api<Page<CatalogProduct>>('/products', { query: { ...f } });
export const createProduct = (body: CreateProductBody) => api<CatalogProduct>('/products', { method: 'POST', body });
export const updateProduct = (id: string, body: UpdateProductBody) =>
  api<CatalogProduct>(`/products/${id}`, { method: 'PATCH', body });
export const archiveProduct = (id: string) => api<CatalogProduct>(`/products/${id}/archive`, { method: 'POST' });
export const restoreProduct = (id: string) => api<CatalogProduct>(`/products/${id}/restore`, { method: 'POST' });

export const listProductTypes = async (includeInactive = true) =>
  asArray<ProductType>(await api<unknown>('/product-types', { query: { includeInactive } }));
export const createProductType = (body: { code: string; name: string; description?: string }) =>
  api<ProductType>('/product-types', { method: 'POST', body });
export const updateProductType = (id: string, body: { name?: string; description?: string; active?: boolean }) =>
  api<ProductType>(`/product-types/${id}`, { method: 'PATCH', body });

export const listUnits = async () => asArray<Unit>(await api<unknown>('/units'));
export const createUnit = (body: { code: string; name: string; allowFractions: boolean }) =>
  api<Unit>('/units', { method: 'POST', body });

export const listAttributeDefinitions = async () =>
  asArray<AttributeDefinition>(await api<unknown>('/attribute-definitions'));
export const createAttributeDefinition = (body: {
  code: string;
  name: string;
  dataType: AttributeDataType;
  options?: string[];
}) => api<AttributeDefinition>('/attribute-definitions', { method: 'POST', body });
