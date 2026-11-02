export const STATUS_TECNICO_ORCAMENTO = [
  'incomplete',
  'complete',
  'validated',
  'report-published',
  'cancelled',
] as const;

export type StatusTecnicoOrcamento = (typeof STATUS_TECNICO_ORCAMENTO)[number];

export interface OrcamentoTecnico {
  readonly id: string;
  readonly code: string;
  readonly name: string | null;
  readonly companyName: string;
  readonly createdDate: string | null;
  readonly technicalStatus: StatusTecnicoOrcamento | null;
}

export interface OrcamentoTecnicoItem {
  readonly testType: string;
  readonly sampleCount: number;
  readonly standard: string | null;
}

export interface OrcamentoTecnicoDetalhe extends OrcamentoTecnico {
  readonly description: string | null;
  readonly laboratoryName: string | null;
  readonly scope: string | null;
  readonly items: readonly OrcamentoTecnicoItem[];
}

export interface PaginaOrcamentosTecnicos {
  readonly items: readonly OrcamentoTecnico[];
  readonly page: number;
  readonly pageSize: number;
  readonly totalItems: number;
}

export interface PesquisaOrcamentosTecnicos {
  readonly search: string;
  readonly status: StatusTecnicoOrcamento | null;
  readonly from: string | null;
  readonly to: string | null;
  readonly page: number;
  readonly pageSize: number;
}
