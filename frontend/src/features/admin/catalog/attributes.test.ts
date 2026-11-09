import { describe, expect, it } from 'vitest';
import { buildAttributes } from './attributes';
import type { AttributeDefinition } from './types';

const def = (code: string, dataType: AttributeDefinition['dataType']): AttributeDefinition => ({
  id: code,
  code,
  name: code,
  dataType,
  active: true,
  options: null,
});

describe('buildAttributes', () => {
  const defs = [def('LENGTH', 'NUMBER'), def('MATERIAL', 'REFERENCE'), def('FRAGILE', 'BOOLEAN'), def('NOTE', 'STRING')];

  it('заполняет поле по типу атрибута', () => {
    const out = buildAttributes(defs, {
      LENGTH: { value: 2000, unitId: 'u1' },
      MATERIAL: { value: 'METAL' },
      FRAGILE: { value: false },
      NOTE: { value: ' текст ' },
    });
    expect(out).toEqual([
      { attributeCode: 'LENGTH', valueNumber: 2000, valueUnitId: 'u1' },
      { attributeCode: 'MATERIAL', valueString: 'METAL' },
      { attributeCode: 'FRAGILE', valueBoolean: false },
      { attributeCode: 'NOTE', valueString: 'текст' },
    ]);
  });

  it('пустые поля не отправляются, но false и 0 остаются', () => {
    const out = buildAttributes(defs, { LENGTH: { value: 0 }, MATERIAL: { value: '' }, FRAGILE: { value: null } });
    expect(out).toEqual([{ attributeCode: 'LENGTH', valueNumber: 0, valueUnitId: undefined }]);
  });
});
