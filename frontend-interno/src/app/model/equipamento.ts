export type EquipamentoTipo = 'TRANSFORMADOR' | 'DISJUNTOR' | 'REATOR' | 'COMUTADOR' | 'TANQUE';

export type EquipamentoRisco = 'SAUDAVEL' | 'ATENCAO' | 'CRITICO';

export interface EquipamentoRequest {
  serialNumber: string;
  tag: string;
  substation: string;
  primaryVoltage: string;
  companyId: string;
  type: EquipamentoTipo;
  riskLevel: EquipamentoRisco;
}

export interface Equipamento extends EquipamentoRequest {
  id: number;
  owner: string;
}

export const EQUIPAMENTO_TIPOS: { value: EquipamentoTipo; label: string }[] = [
  { value: 'TRANSFORMADOR', label: 'Transformador' },
  { value: 'DISJUNTOR', label: 'Disjuntor' },
  { value: 'REATOR', label: 'Reator' },
  { value: 'COMUTADOR', label: 'Comutador' },
  { value: 'TANQUE', label: 'Tanque' },
];

export const EQUIPAMENTO_RISCOS: { value: EquipamentoRisco; label: string }[] = [
  { value: 'SAUDAVEL', label: 'Saudável' },
  { value: 'ATENCAO', label: 'Atenção' },
  { value: 'CRITICO', label: 'Crítico' },
];
