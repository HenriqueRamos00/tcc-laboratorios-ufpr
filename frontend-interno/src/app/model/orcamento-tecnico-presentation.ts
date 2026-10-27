import {
  OrcamentoTecnico,
  StatusTecnicoOrcamento,
  STATUS_TECNICO_ORCAMENTO,
} from './orcamento-tecnico';

export interface OpcaoStatusTecnicoOrcamento {
  readonly value: StatusTecnicoOrcamento;
  readonly label: string;
}

export interface OrcamentoTecnicoApresentacao extends OrcamentoTecnico {
  readonly createdDateLabel: string;
  readonly technicalStatusLabel: string;
  readonly technicalStatusClass: string;
}

const ROTULOS_STATUS_TECNICO: Readonly<Record<StatusTecnicoOrcamento, string>> = {
  incomplete: 'Incompleto',
  complete: 'Completo',
  validated: 'Validado',
  'report-published': 'Publicado',
  cancelled: 'Cancelado',
};

const CLASSE_STATUS_AUSENTE = 'bg-lactec-line-soft text-lactec-ink-soft';

const CLASSES_STATUS_TECNICO: Readonly<Record<StatusTecnicoOrcamento, string>> = {
  incomplete: 'bg-lactec-danger-bg text-lactec-danger-ink',
  complete: 'bg-lactec-pill-pend-bg text-lactec-pill-pend-ink',
  validated: 'bg-lactec-pill-proc-bg text-lactec-pill-proc-ink',
  'report-published': 'bg-lactec-pill-done-bg text-lactec-pill-done-ink',
  cancelled: CLASSE_STATUS_AUSENTE,
};

const FUSO_HORARIO_PORTAL = 'America/Sao_Paulo';
const FORMATADOR_DATA_CRIACAO = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  timeZone: FUSO_HORARIO_PORTAL,
});

export const OPCOES_STATUS_TECNICO: readonly OpcaoStatusTecnicoOrcamento[] =
  STATUS_TECNICO_ORCAMENTO.map((value) => ({
    value,
    label: ROTULOS_STATUS_TECNICO[value],
  }));

export function apresentarStatusTecnico(
  status: StatusTecnicoOrcamento | null,
): { readonly label: string; readonly className: string } {
  if (status === null) {
    return { label: 'Não informado', className: CLASSE_STATUS_AUSENTE };
  }

  return {
    label: ROTULOS_STATUS_TECNICO[status],
    className: CLASSES_STATUS_TECNICO[status],
  };
}

export function formatarDataCriacaoTecnica(dataIso: string | null): string {
  if (!dataIso) return '-';

  const data = new Date(dataIso);
  return Number.isNaN(data.getTime()) ? '-' : FORMATADOR_DATA_CRIACAO.format(data);
}

export function apresentarOrcamentoTecnico(
  orcamento: OrcamentoTecnico,
): OrcamentoTecnicoApresentacao {
  const status = apresentarStatusTecnico(orcamento.technicalStatus);
  return {
    ...orcamento,
    createdDateLabel: formatarDataCriacaoTecnica(orcamento.createdDate),
    technicalStatusLabel: status.label,
    technicalStatusClass: status.className,
  };
}
