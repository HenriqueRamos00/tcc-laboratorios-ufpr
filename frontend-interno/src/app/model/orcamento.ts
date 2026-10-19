export interface Orcamento {
  readonly id: string;
  readonly code: string;
  readonly name?: string | null;
  readonly status: string;
  readonly stage: string;
  readonly description: string;
  readonly companyName: string;
  readonly externalContactName: string;
  readonly externalContactEmail: string;
  readonly totalPrice: number;
  readonly createdDate?: string | null;
  readonly hasDocument: boolean;
}

export interface OrcamentoItem {
  readonly testType: string;
  readonly sampleCount: number;
  readonly standard: string | null;
  readonly unitPrice: number;
}

export interface OrcamentoDetalhe extends Orcamento {
  readonly companyId: string;
  readonly laboratoryName: string | null;
  readonly scope: string | null;
  readonly items: readonly OrcamentoItem[];
}

export interface OrcamentoRequest {
  readonly code: string;
  readonly companyId: string;
  readonly name: string;
  readonly laboratoryName: string;
  readonly description: string;
  readonly scope: string | null;
  readonly externalContactName: string;
  readonly externalContactEmail: string;
  readonly status: string;
  readonly totalPrice: number;
  readonly items: readonly OrcamentoItem[];
}

export interface PaginaOrcamentos {
  readonly items: readonly Orcamento[];
  readonly page: number;
  readonly pageSize: number;
  readonly totalItems: number;
}

export interface PesquisaOrcamentos {
  readonly search: string;
  readonly status: string | null;
  readonly page: number;
  readonly pageSize: number;
}
