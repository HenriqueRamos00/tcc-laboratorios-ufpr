export type InsulatingOilKind =
  | 'mineral'
  | 'natural-ester'
  | 'silicone'
  | 'not-informed'
  | 'unrecognized';

export interface OilType {
  readonly kind: InsulatingOilKind;
  readonly reported: string | null;
}

export type PhysicalChemicalLimitAbsence =
  | 'oil-type-without-criterion'
  | 'oil-type-not-informed'
  | 'oil-type-unrecognized';

export const LIMIT_ABSENCE_TEXT: Record<PhysicalChemicalLimitAbsence, string> = {
  'oil-type-without-criterion': 'Sem critério para este tipo de óleo',
  'oil-type-not-informed': 'Tipo do óleo não informado',
  'oil-type-unrecognized': 'Tipo do óleo não reconhecido',
};

const OIL_TYPE_LABEL: Record<Exclude<InsulatingOilKind, 'unrecognized'>, string> = {
  mineral: 'Óleo mineral isolante',
  'natural-ester': 'Óleo vegetal (éster natural)',
  silicone: 'Óleo de silicone',
  'not-informed': 'Tipo do óleo não informado',
};

const OIL_TYPE_SHORT_LABEL: Record<InsulatingOilKind, string> = {
  mineral: 'Mineral',
  'natural-ester': 'Vegetal',
  silicone: 'Silicone',
  'not-informed': 'tipo do óleo não informado',
  unrecognized: 'tipo do óleo não reconhecido',
};

const PHYSICAL_CHEMICAL_CRITERIA: Record<InsulatingOilKind, string | null> = {
  mineral: 'ABNT NBR 10576/17',
  'natural-ester': null,
  silicone: null,
  'not-informed': null,
  unrecognized: null,
};

export function oilTypeLabel(oil: OilType): string {
  if (oil.kind === 'unrecognized') {
    return oil.reported ? `${oil.reported} (tipo não reconhecido)` : 'Tipo do óleo não reconhecido';
  }
  return OIL_TYPE_LABEL[oil.kind];
}

export function oilTypeShortLabel(oil: OilType): string {
  return OIL_TYPE_SHORT_LABEL[oil.kind];
}

export function physicalChemicalCriteriaOf(oil: OilType): string | null {
  return PHYSICAL_CHEMICAL_CRITERIA[oil.kind];
}

export function isKnownOilType(oil: OilType): boolean {
  return oil.kind !== 'not-informed' && oil.kind !== 'unrecognized';
}
