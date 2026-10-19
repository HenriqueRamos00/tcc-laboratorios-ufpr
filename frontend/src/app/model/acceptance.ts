// Termo de Aceite (Anexo II da proposta): HU-09.

export interface BillingAddress {
  company: string;
  cnpj: string;
  stateRegistration: string;
  street: string;
  city: string;
  state: string;
  district: string;
  postalCode: string;
  contact: string;
  phone: string;
  fax: string;
  invoiceEmails: string;
}

export interface AcceptanceApproval {
  requesterName: string;
  responsibleName: string;
  jobTitle: string;
  department: string;
  date: string;
  allowsResultsByEmail: boolean;
  resultsEmail: string;
}

// O endereço de entrega do relatório saiu do termo: o laudo é publicado no
// portal e o cliente baixa de lá, então não há mais despacho de via impressa
// para endereçar. O que restou do endereço é o da cobrança, que a nota fiscal
// continua exigindo.
export interface AcceptanceTerms {
  readonly proposalId: string;
  readonly billing: BillingAddress;
  readonly approval: AcceptanceApproval;
}

/** Como o cliente autoriza a execução: ordem de compra ou termo preenchido. */
export type ApprovalMethod = 'ordem-de-compra' | 'termo-de-aceite';

export interface QuoteRejection {
  readonly proposalId: string;
  readonly justification: string;
}
