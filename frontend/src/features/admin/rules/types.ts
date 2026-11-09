export interface NumericCondition {
  eq?: number | null;
  min?: number | null;
  max?: number | null;
  /** Код единицы; без него берётся единица по умолчанию (мм для размеров). */
  unit?: string | null;
}

/** Условия объединяются по «И». Числовые атрибуты: LENGTH, WIDTH, HEIGHT, DIAMETER. */
export interface RuleConditions {
  productTypeCodes: string[];
  materials: string[];
  numeric: Record<string, NumericCondition>;
}

export const NUMERIC_ATTRIBUTES: { value: string; label: string }[] = [
  { value: 'LENGTH', label: 'Длина' },
  { value: 'WIDTH', label: 'Ширина' },
  { value: 'HEIGHT', label: 'Высота' },
  { value: 'DIAMETER', label: 'Диаметр' },
];

export interface PlacementRule {
  id: string;
  warehouseId: string;
  name: string;
  priority: number;
  active: boolean;
  fallback: boolean;
  ruleVersion: number;
  conditions: RuleConditions;
  targetSectionId: string;
  targetSectionCode: string;
}

export interface RuleSnapshot {
  ruleVersion: number;
  name: string;
  priority: number;
  active: boolean;
  fallback: boolean;
  conditions: RuleConditions;
  targetSectionId: string;
}

export interface RuleVersion {
  version: number;
  changedAt: string;
  changedBy: string | null;
  snapshot: RuleSnapshot;
}

export interface CreateRuleBody {
  name: string;
  priority?: number;
  fallback?: boolean;
  conditions: RuleConditions;
  targetSectionId: string;
}

export interface UpdateRuleBody {
  name?: string;
  priority?: number;
  active?: boolean;
  fallback?: boolean;
  conditions?: RuleConditions;
  targetSectionId?: string;
}
