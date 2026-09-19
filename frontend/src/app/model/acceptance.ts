// Termo de Aceite (Anexo II da proposta): HU-09.

export interface EnderecoDoAceite {
  empresa: string;
  cnpj: string;
  inscricaoEstadual: string;
  endereco: string;
  cidade: string;
  uf: string;
  bairro: string;
  cep: string;
  contato: string;
  telefone: string;
  fax: string;
  emailsParaNotaFiscal: string;
}

export interface AprovacaoDoAceite {
  nomeDoSolicitante: string;
  nomeDoResponsavel: string;
  cargo: string;
  setor: string;
  data: string;
  autorizaEnvioPorEmail: boolean;
  emailParaResultados: string;
}

export interface TermoDeAceite {
  readonly propostaId: string;
  readonly cobranca: EnderecoDoAceite;
  readonly envioDoRelatorio: EnderecoDoAceite;
  readonly mesmoEnderecoDaCobranca: boolean;
  readonly aprovacao: AprovacaoDoAceite;
}

/** Como o cliente autoriza a execução: ordem de compra ou termo preenchido. */
export type FormaDeAprovacao = 'ordem-de-compra' | 'termo-de-aceite';

export interface RecusaDeOrcamento {
  readonly propostaId: string;
  readonly justificativa: string;
}

export function enderecoVazio(): EnderecoDoAceite {
  return {
    empresa: '',
    cnpj: '',
    inscricaoEstadual: '',
    endereco: '',
    cidade: '',
    uf: '',
    bairro: '',
    cep: '',
    contato: '',
    telefone: '',
    fax: '',
    emailsParaNotaFiscal: '',
  };
}
