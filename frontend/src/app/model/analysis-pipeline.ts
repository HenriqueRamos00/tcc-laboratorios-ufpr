// O dado é uma linha só, apresentada ao cliente em três recortes que se
// sobrepõem nas bordas de propósito: a última etapa de um é a primeira do
// próximo, e é assim que a tela mostra a passagem de bastão entre o comercial,
// o recebimento da amostra e o laboratório.
//
// "Protocolado" e "Em execução" vêm das telas de Etapas da Análise Técnica e
// não constam da lista escrita do critério 3 da HU-03.

export type EtapaDaLinha =
  | 'qualificacao'
  | 'analise-da-area'
  | 'elaborando-proposta'
  | 'negociacao'
  | 'aprovado-pelo-cliente'
  | 'aguardando-amostra'
  | 'recebido'
  | 'protocolado'
  | 'em-execucao'
  | 'incompleto'
  | 'completo'
  | 'validado'
  | 'finalizado'
  | 'elaborando-relatorio'
  | 'relatorio-publicado';

export type EtapaTerminal = 'cancelado' | 'recusado';

export type SituacaoDaSolicitacao = EtapaDaLinha | EtapaTerminal;

export interface DescricaoDaEtapa {
  readonly chave: EtapaDaLinha;
  readonly rotulo: string;
  /** Ligadura do Material Symbols, usada quando a etapa é a atual. */
  readonly icone: string;
}

export const LINHA_DE_ETAPAS: readonly DescricaoDaEtapa[] = [
  { chave: 'qualificacao', rotulo: 'Qualificação', icone: 'fact_check' },
  { chave: 'analise-da-area', rotulo: 'Em análise pela área', icone: 'hourglass_empty' },
  { chave: 'elaborando-proposta', rotulo: 'Elaborando proposta', icone: 'description' },
  { chave: 'negociacao', rotulo: 'Em negociação', icone: 'attach_money' },
  { chave: 'aprovado-pelo-cliente', rotulo: 'Aprovado pelo cliente', icone: 'how_to_reg' },
  { chave: 'aguardando-amostra', rotulo: 'Aguardando entrega da amostra', icone: 'inventory_2' },
  { chave: 'recebido', rotulo: 'Recebido', icone: 'inbox' },
  { chave: 'protocolado', rotulo: 'Protocolado', icone: 'assignment_turned_in' },
  { chave: 'em-execucao', rotulo: 'Em execução', icone: 'science' },
  { chave: 'incompleto', rotulo: 'Incompleto', icone: 'warning_amber' },
  { chave: 'completo', rotulo: 'Completo', icone: 'done_all' },
  { chave: 'validado', rotulo: 'Validado', icone: 'autorenew' },
  { chave: 'finalizado', rotulo: 'Finalizado', icone: 'task_alt' },
  { chave: 'elaborando-relatorio', rotulo: 'Elaborando relatório', icone: 'list_alt' },
  { chave: 'relatorio-publicado', rotulo: 'Relatório publicado', icone: 'cloud_done' },
];

const INDICE_DA_ETAPA = new Map<EtapaDaLinha, number>(
  LINHA_DE_ETAPAS.map((etapa, indice) => [etapa.chave, indice]),
);

export function indiceDaEtapa(etapa: EtapaDaLinha): number {
  return INDICE_DA_ETAPA.get(etapa) ?? 0;
}

export function ehTerminal(situacao: SituacaoDaSolicitacao): situacao is EtapaTerminal {
  return situacao === 'cancelado' || situacao === 'recusado';
}

export type ChaveDoGrupo = 'proposta' | 'amostras' | 'laboratorio';

export interface GrupoDaLinha {
  readonly chave: ChaveDoGrupo;
  readonly titulo: string;
  readonly icone: string;
  /** Índice inicial e final (inclusivo) dentro de LINHA_DE_ETAPAS. */
  readonly de: number;
  readonly ate: number;
}

export const GRUPOS_DA_LINHA: readonly GrupoDaLinha[] = [
  { chave: 'proposta', titulo: 'Status da Proposta', icone: 'description', de: 0, ate: 6 },
  {
    chave: 'amostras',
    titulo: 'Etapas da Análise Técnica das amostras',
    icone: 'science',
    de: 6,
    ate: 10,
  },
  {
    chave: 'laboratorio',
    titulo: 'Procedimento dos Laboratórios',
    icone: 'biotech',
    de: 10,
    ate: 14,
  },
];

/** Nas bordas, onde dois recortes contêm a etapa, o último vence. */
export function grupoDaEtapa(indice: number): GrupoDaLinha {
  const encontrados = GRUPOS_DA_LINHA.filter((grupo) => indice >= grupo.de && indice <= grupo.ate);
  return encontrados.at(-1) ?? GRUPOS_DA_LINHA[0];
}

export type ChaveDaAba = 'todos' | 'pendentes' | 'andamento' | 'finalizados' | 'cancelados';

// Mapa completo por construção: etapa nova sem classificação aqui vira erro de
// compilação, em vez de sumir da listagem em silêncio.
const ABA_DA_ETAPA: Record<EtapaDaLinha, Exclude<ChaveDaAba, 'todos'>> = {
  qualificacao: 'pendentes',
  'analise-da-area': 'pendentes',
  'elaborando-proposta': 'pendentes',
  negociacao: 'pendentes',
  'aprovado-pelo-cliente': 'andamento',
  'aguardando-amostra': 'andamento',
  recebido: 'andamento',
  protocolado: 'andamento',
  'em-execucao': 'andamento',
  incompleto: 'andamento',
  completo: 'andamento',
  validado: 'andamento',
  finalizado: 'andamento',
  'elaborando-relatorio': 'andamento',
  'relatorio-publicado': 'finalizados',
};

export function abaDaSituacao(situacao: SituacaoDaSolicitacao): Exclude<ChaveDaAba, 'todos'> {
  return ehTerminal(situacao) ? 'cancelados' : ABA_DA_ETAPA[situacao];
}
