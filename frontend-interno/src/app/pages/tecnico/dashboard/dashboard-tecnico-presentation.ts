import { ReportValidationStatus } from '@/app/model/relatorio';

export type IndicadorDashboardTecnico =
  | 'testsInExecution'
  | 'testsAwaitingApproval'
  | 'testsFinished';

export interface CardIndicadorDashboardTecnico {
  readonly chave: IndicadorDashboardTecnico;
  readonly rotulo: string;
  readonly icone: string;
}

export const INDICADORES_DASHBOARD_TECNICO: readonly CardIndicadorDashboardTecnico[] = [
  {
    chave: 'testsInExecution',
    rotulo: 'Ensaios em execução',
    icone: 'science',
  },
  {
    chave: 'testsAwaitingApproval',
    rotulo: 'Ensaios pendentes de aprovação',
    icone: 'pending_actions',
  },
  {
    chave: 'testsFinished',
    rotulo: 'Ensaios finalizados',
    icone: 'task_alt',
  },
];

export interface AtalhoDashboardTecnico {
  readonly rotulo: string;
  readonly descricao: string;
  readonly icone: string;
  readonly rota: string;
}

export const ATALHOS_DASHBOARD_TECNICO: readonly AtalhoDashboardTecnico[] = [
  {
    rotulo: 'Manter ensaio',
    descricao: 'Consulte os ensaios cadastrados e atualize suas informações.',
    icone: 'science',
    rota: '/tecnico/ensaios',
  },
  {
    rotulo: 'Novo ensaio',
    descricao: 'Cadastre uma nova execução de ensaio.',
    icone: 'add_circle_outline',
    rota: '/tecnico/ensaios/novo',
  },
  {
    rotulo: 'Pesquisar relatório',
    descricao: 'Pesquise os laudos técnicos e acompanhe sua validação.',
    icone: 'description',
    rota: '/tecnico/relatorios',
  },
  {
    rotulo: 'Equipamentos',
    descricao: 'Consulte os equipamentos cadastrados no sistema.',
    icone: 'precision_manufacturing',
    rota: '/tecnico/equipamentos',
  },
];

const FORMATADOR_QUANTIDADE = new Intl.NumberFormat('pt-BR', {
  maximumFractionDigits: 0,
});

export function resumoLaudosPendentes(total: number, exibidos: number): string {
  if (total <= 0) return '';

  const totalFormatado = FORMATADOR_QUANTIDADE.format(total);
  const exibidosFormatados = FORMATADOR_QUANTIDADE.format(exibidos);

  if (exibidos < total) {
    return `Exibindo ${exibidosFormatados} de ${totalFormatado} laudos pendentes de validação.`;
  }

  return total === 1
    ? '1 laudo pendente de validação.'
    : `${totalFormatado} laudos pendentes de validação.`;
}

export function classeStatusLaudoDashboard(status: ReportValidationStatus): string {
  const base = 'inline-flex max-w-full whitespace-normal rounded-full px-3 py-1 text-xs font-semibold [overflow-wrap:anywhere]';
  const estado = status === 'validated'
    ? 'bg-emerald-50 text-emerald-800'
    : 'bg-amber-50 text-amber-900';

  return `${base} ${estado}`;
}
