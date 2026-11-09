import type { AttributeDefinition, AttributeValueBody } from './types';

export interface AttrField {
  value?: string | number | boolean | null;
  unitId?: string;
}

const isEmpty = (v: unknown) => v === undefined || v === null || v === '';

/** Собирает значения атрибутов в тело запроса. Пустые поля не отправляются. */
export function buildAttributes(defs: AttributeDefinition[], attrs: Record<string, AttrField> = {}): AttributeValueBody[] {
  const out: AttributeValueBody[] = [];
  for (const d of defs) {
    const f = attrs[d.code];
    if (!f || isEmpty(f.value)) continue;
    switch (d.dataType) {
      case 'NUMBER':
        out.push({ attributeCode: d.code, valueNumber: Number(f.value), valueUnitId: f.unitId || undefined });
        break;
      case 'BOOLEAN':
        out.push({ attributeCode: d.code, valueBoolean: Boolean(f.value) });
        break;
      default:
        out.push({ attributeCode: d.code, valueString: String(f.value).trim() });
    }
  }
  return out;
}
