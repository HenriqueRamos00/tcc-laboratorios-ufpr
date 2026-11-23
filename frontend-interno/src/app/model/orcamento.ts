export interface Orcamento {
  readonly id: string;
  readonly code: string;
  readonly name?: string | null;
  readonly status: string;
  readonly stage: string;
  readonly description: string;
  readonly companyName: string;
  readonly protocol: string;
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
  readonly clientDecision?: DecisaoClienteInterna | null;
}

export interface DecisaoClienteInterna {
  readonly acceptance: AceiteClienteInterno | null;
  readonly rejection: RecusaClienteInterna | null;
}

export interface AceiteClienteInterno {
  readonly method: string;
  readonly acceptedAt: string;
  readonly terms: TermoAceiteCliente | null;
  readonly hasPurchaseOrder: boolean;
}

export interface RecusaClienteInterna {
  readonly refusedAt: string;
  readonly justification: string | null;
}

export interface TermoAceiteCliente {
  readonly billing: DadosCobrancaTermoAceite;
  readonly approval: DadosAprovacaoTermoAceite;
}

export interface DadosCobrancaTermoAceite {
  readonly company: string;
  readonly cnpj: string;
  readonly stateRegistration: string | null;
  readonly street: string;
  readonly city: string;
  readonly state: string;
  readonly district: string | null;
  readonly postalCode: string;
  readonly contact: string | null;
  readonly phone: string | null;
  readonly fax: string | null;
  readonly invoiceEmails: string | null;
}

export interface DadosAprovacaoTermoAceite {
  readonly requesterName: string;
  readonly responsibleName: string;
  readonly jobTitle: string;
  readonly department: string | null;
  readonly date: string;
  readonly allowsResultsByEmail: boolean;
  readonly resultsEmail: string | null;
}

export interface OrcamentoRequest {
  readonly code: string;
  readonly companyId: string;
  readonly name: string;
  readonly laboratoryName: string;
  readonly protocol: string;
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
