export type SectionStatus = 'ACTIVE' | 'BLOCKED' | 'ARCHIVED';

/** Ограничения секции. Размеры в мм. Пустые наборы и отсутствующие границы означают «любой». */
export interface SectionConstraints {
  lengthMin: number | null;
  lengthMax: number | null;
  widthMin: number | null;
  widthMax: number | null;
  heightMin: number | null;
  heightMax: number | null;
  maxCapacity: number | null;
  capacityUnitCode: string | null;
  allowedProductTypes: string[] | null;
  allowedMaterials: string[] | null;
}

export interface Section {
  id: string;
  warehouseId: string;
  code: string;
  name: string;
  sectionType: string | null;
  zone: string | null;
  rack: string | null;
  priority: number;
  status: SectionStatus;
  /** Служебная зона «Нераспределено»: ограничения и статус ей менять нельзя. */
  unassignedZone: boolean;
  constraints: SectionConstraints | null;
}

export interface SectionBody {
  name?: string;
  sectionType?: string;
  zone?: string;
  rack?: string;
  priority?: number;
  status?: SectionStatus;
  constraints?: Partial<SectionConstraints>;
}

export interface CreateSectionBody extends SectionBody {
  warehouseId: string;
  code: string;
  name: string;
}
