// Equipamento do cliente (HU-05 e HU-06). Origem: AutoLAB.

export interface Equipamento {
  readonly id: string;
  readonly numeroDeSerie: string;
  readonly tag: string;
  readonly tipo: string;
  readonly localizacao: string;
}

export interface DetalheDoEquipamento extends Equipamento {
  readonly tipoDoOleo: string;
  readonly empresa: string;
  readonly tensao: string;
  readonly potencia: string;
}

/** Linha do histórico de relatórios exibido no detalhe do equipamento. */
export interface RelatorioDoEquipamento {
  readonly id: string;
  readonly data: string;
  readonly tipoDeAnalise: string;
  readonly situacao: 'Concluído' | 'Em andamento' | 'Cancelado';
}
