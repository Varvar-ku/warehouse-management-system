export type AttributeDataType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'REFERENCE';

export interface ProductType {
  id: string;
  code: string;
  name: string;
  description: string | null;
  active: boolean;
}

export interface Unit {
  id: string;
  code: string;
  name: string;
  allowFractions: boolean;
}

export interface AttributeDefinition {
  id: string;
  code: string;
  name: string;
  dataType: AttributeDataType;
  active: boolean;
  options: string[] | null;
}

export interface AttributeValue {
  attributeCode: string;
  valueString: string | null;
  valueNumber: number | null;
  valueBoolean: boolean | null;
  valueUnitId: string | null;
  valueUnitCode: string | null;
}

export interface CatalogProduct {
  id: string;
  sku: string;
  name: string;
  productTypeId: string;
  productTypeCode: string;
  unitId: string;
  unitCode: string;
  unitAllowFractions: boolean;
  description: string | null;
  active: boolean;
  attributes: AttributeValue[];
  createdAt: string;
  updatedAt: string;
}

/** Значение атрибута в теле запроса; заполняется поле, соответствующее типу атрибута. */
export interface AttributeValueBody {
  attributeCode: string;
  valueString?: string;
  valueNumber?: number;
  valueBoolean?: boolean;
  valueUnitId?: string;
}

export interface CreateProductBody {
  sku: string;
  name: string;
  productTypeId: string;
  unitId: string;
  description?: string;
  attributes?: AttributeValueBody[];
}

/** SKU и единицу менять нельзя. Если передан attributes, набор заменяется целиком. */
export interface UpdateProductBody {
  name?: string;
  productTypeId?: string;
  description?: string;
  attributes?: AttributeValueBody[];
}
