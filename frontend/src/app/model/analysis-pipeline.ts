// O dado é uma linha só, apresentada ao cliente em três recortes que se
// sobrepõem nas bordas de propósito: a última etapa de um é a primeira do
// próximo, e é assim que a tela mostra a passagem de bastão entre o comercial,
// o recebimento da amostra e o laboratório.
//
// "Protocolado" e "Em execução" vêm das telas de Etapas da Análise Técnica e
// não constam da lista escrita do critério 3 da HU-03.

export type PipelineStage =
  | 'qualificacao'
  | 'analise-da-area'
  | 'elaborando-proposta'
  | 'negociacao'
  | 'aprovado-pelo-cliente'
  | 'aguardando-amostra'
  | 'recebido'
  | 'protocolado'
  | 'em-execucao'
  | 'completo'
  | 'validado'
  | 'finalizado'
  | 'elaborando-relatorio'
  | 'relatorio-publicado';

export type ClosedStage = 'cancelado' | 'recusado';

export type RequestStatus = PipelineStage | ClosedStage;

export interface StageDescription {
  readonly key: PipelineStage;
  readonly label: string;
  /** Ligadura do Material Symbols, usada quando a etapa é a atual. */
  readonly icon: string;
}

export const PIPELINE_STAGES: readonly StageDescription[] = [
  { key: 'qualificacao', label: 'Qualificação', icon: 'fact_check' },
  { key: 'analise-da-area', label: 'Em análise pela área', icon: 'hourglass_empty' },
  { key: 'elaborando-proposta', label: 'Elaborando proposta', icon: 'description' },
  { key: 'negociacao', label: 'Em negociação', icon: 'attach_money' },
  { key: 'aprovado-pelo-cliente', label: 'Aprovado pelo cliente', icon: 'how_to_reg' },
  { key: 'aguardando-amostra', label: 'Aguardando entrega da amostra', icon: 'inventory_2' },
  { key: 'recebido', label: 'Recebido', icon: 'inbox' },
  { key: 'protocolado', label: 'Protocolado', icon: 'assignment_turned_in' },
  { key: 'em-execucao', label: 'Em execução', icon: 'science' },
  { key: 'completo', label: 'Completo', icon: 'done_all' },
  { key: 'validado', label: 'Validado', icon: 'autorenew' },
  { key: 'finalizado', label: 'Finalizado', icon: 'task_alt' },
  { key: 'elaborando-relatorio', label: 'Elaborando relatório', icon: 'list_alt' },
  { key: 'relatorio-publicado', label: 'Relatório publicado', icon: 'cloud_done' },
];

const STAGE_INDEX_BY_KEY = new Map<PipelineStage, number>(
  PIPELINE_STAGES.map((stage, index) => [stage.key, index]),
);

export function stageIndex(stage: PipelineStage): number {
  return STAGE_INDEX_BY_KEY.get(stage) ?? 0;
}

export function isClosed(status: RequestStatus): status is ClosedStage {
  return status === 'cancelado' || status === 'recusado';
}

export type GroupKey = 'proposta' | 'amostras' | 'laboratorio';

export interface PipelineGroup {
  readonly key: GroupKey;
  readonly title: string;
  readonly icon: string;
  /** Índice inicial e final (inclusivo) dentro de PIPELINE_STAGES. */
  readonly de: number;
  readonly ate: number;
}

export const PIPELINE_GROUPS: readonly PipelineGroup[] = [
  { key: 'proposta', title: 'Status da Proposta', icon: 'description', de: 0, ate: 6 },
  {
    key: 'amostras',
    title: 'Etapas da Análise Técnica das amostras',
    icon: 'science',
    de: 6,
    ate: 9,
  },
  {
    key: 'laboratorio',
    title: 'Procedimento dos Laboratórios',
    icon: 'biotech',
    de: 9,
    ate: 13,
  },
];

/** Nas bordas, onde dois recortes contêm a etapa, o último vence. */
export function groupOfStage(index: number): PipelineGroup {
  const encontrados = PIPELINE_GROUPS.filter((group) => index >= group.de && index <= group.ate);
  return encontrados.at(-1) ?? PIPELINE_GROUPS[0];
}

export type TabKey = 'todos' | 'pendentes' | 'andamento' | 'finalizados' | 'cancelados';

// Mapa completo por construção: etapa nova sem classificação aqui vira erro de
// compilação, em vez de sumir da listagem em silêncio.
const TAB_BY_STAGE: Record<PipelineStage, Exclude<TabKey, 'todos'>> = {
  qualificacao: 'pendentes',
  'analise-da-area': 'pendentes',
  'elaborando-proposta': 'pendentes',
  negociacao: 'pendentes',
  'aprovado-pelo-cliente': 'andamento',
  'aguardando-amostra': 'andamento',
  recebido: 'andamento',
  protocolado: 'andamento',
  'em-execucao': 'andamento',
  completo: 'andamento',
  validado: 'andamento',
  finalizado: 'andamento',
  'elaborando-relatorio': 'andamento',
  'relatorio-publicado': 'finalizados',
};

export function tabForStatus(status: RequestStatus): Exclude<TabKey, 'todos'> {
  return isClosed(status) ? 'cancelados' : TAB_BY_STAGE[status];
}
