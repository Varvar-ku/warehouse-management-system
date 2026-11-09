import { describe, expect, it } from 'vitest';
import { describeConditions, fromConditions, toConditions } from './conditions';

describe('условия правила размещения', () => {
  it('равенство: eq и единица', () => {
    const c = toConditions({ numeric: [{ attr: 'LENGTH', mode: 'eq', eq: 2000, unit: 'MM' }] });
    expect(c.numeric).toEqual({ LENGTH: { eq: 2000, unit: 'MM' } });
  });

  it('диапазон: min и max без eq', () => {
    const c = toConditions({ numeric: [{ attr: 'WIDTH', mode: 'range', min: 10, max: null }] });
    expect(c.numeric.WIDTH).toEqual({ min: 10, max: null, unit: null });
  });

  it('пустые материалы отбрасываются, пробелы обрезаются', () => {
    const c = toConditions({ materials: [' METAL ', '  '] });
    expect(c.materials).toEqual(['METAL']);
  });

  it('строки без атрибута пропускаются', () => {
    expect(toConditions({ numeric: [{ mode: 'eq', eq: 1 }] }).numeric).toEqual({});
  });

  it('туда и обратно сохраняет режим строки', () => {
    const rows = fromConditions({
      productTypeCodes: [],
      materials: [],
      numeric: { LENGTH: { eq: 5, unit: 'MM' }, WIDTH: { min: 1, max: 2 } },
    });
    expect(rows.find((r) => r.attr === 'LENGTH')?.mode).toBe('eq');
    expect(rows.find((r) => r.attr === 'WIDTH')?.mode).toBe('range');
  });

  it('описание условий читается человеком', () => {
    expect(describeConditions({ productTypeCodes: [], materials: [], numeric: {} })).toBe('без условий');
    expect(
      describeConditions({
        productTypeCodes: ['ARMATURE'],
        materials: ['METAL'],
        numeric: { LENGTH: { eq: 2000, unit: 'MM' } },
      }),
    ).toBe('типы: ARMATURE; материалы: METAL; Длина = 2000 MM');
  });
});
