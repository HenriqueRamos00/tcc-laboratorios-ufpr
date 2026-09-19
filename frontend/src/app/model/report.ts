// Relatórios publicados de uma solicitação concluída (HU-04).

export interface RelatorioPublicado {
  readonly id: string;
  readonly codigo: string;
  readonly protocolo: string;
  readonly amostra: string;
  readonly laboratorio: string;
  readonly publicadoEm: string;
}

export interface ConclusaoDaAnalise {
  readonly protocolo: string;
  readonly tipoDeEnsaio: string;
  readonly resumo: string;
  readonly relatorios: readonly RelatorioPublicado[];
}
