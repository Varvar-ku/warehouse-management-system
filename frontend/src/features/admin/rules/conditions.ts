import { NUMERIC_ATTRIBUTES, type NumericCondition, type RuleConditions } from './types';

export interface NumericRow {
  attr?: string;
  mode?: 'eq' | 'range';
  eq?: number | null;
  min?: number | null;
  max?: number | null;
  unit?: string;
}

export interface ConditionFormValues {
  productTypeCodes?: string[];
  materials?: string[];
  numeric?: NumericRow[];
}

/** Строки формы → условия правила (RuleConditions бэкенда). */
export function toConditions(v: ConditionFormValues): RuleConditions {
  const numeric: Record<string, NumericCondition> = {};
  for (const r of v.numeric ?? []) {
    if (!r.attr) continue;
    numeric[r.attr] =
      r.mode === 'range'
        ? { min: r.min ?? null, max: r.max ?? null, unit: r.unit || null }
        : { eq: r.eq ?? null, unit: r.unit || null };
  }
  return {
    productTypeCodes: v.productTypeCodes ?? [],
    materials: (v.materials ?? []).map((m) => m.trim()).filter(Boolean),
    numeric,
  };
}

/** Условия правила → строки формы (обратное преобразование). */
export const fromConditions = (c: RuleConditions): NumericRow[] =>
  Object.entries(c.numeric ?? {}).map(([attr, n]) =>
    n.eq !== null && n.eq !== undefined
      ? { attr, mode: 'eq', eq: n.eq, unit: n.unit ?? undefined }
      : { attr, mode: 'range', min: n.min, max: n.max, unit: n.unit ?? undefined },
  );

const attrLabel = (code: string) => NUMERIC_ATTRIBUTES.find((a) => a.value === code)?.label ?? code;

/** Краткое описание условий для таблицы и истории версий. */
export function describeConditions(c: RuleConditions): string {
  const parts: string[] = [];
  if (c.productTypeCodes?.length) parts.push(`типы: ${c.productTypeCodes.join(', ')}`);
  if (c.materials?.length) parts.push(`материалы: ${c.materials.join(', ')}`);
  const has = (x: number | null | undefined): x is number => x !== null && x !== undefined;
  for (const [attr, n] of Object.entries(c.numeric ?? {})) {
    const unit = n.unit ? ` ${n.unit}` : '';
    if (has(n.eq)) parts.push(`${attrLabel(attr)} = ${n.eq}${unit}`);
    else if (has(n.min) && has(n.max)) parts.push(`${attrLabel(attr)} ${n.min}–${n.max}${unit}`);
    else if (has(n.min)) parts.push(`${attrLabel(attr)} от ${n.min}${unit}`);
    else if (has(n.max)) parts.push(`${attrLabel(attr)} до ${n.max}${unit}`);
  }
  return parts.length ? parts.join('; ') : 'без условий';
}
