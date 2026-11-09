export interface Warehouse {
  id: string;
  code: string;
  name: string;
  address: string | null;
  active: boolean;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  unitCode: string;
  unitAllowFractions: boolean;
  active: boolean;
}
