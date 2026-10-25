import { DashboardAdministrador } from '@/app/model/dashboard-administrador';

const FORMATADOR_MOEDA_BRL = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatarMoedaBRLPorGrupos(valor: number): readonly string[] {
  const partes = FORMATADOR_MOEDA_BRL.formatToParts(valor);
  const indiceInteiro = partes.findIndex((parte) => parte.type === 'integer');

  if (indiceInteiro < 0) {
    return [FORMATADOR_MOEDA_BRL.format(valor)];
  }

  const prefixo = partes.slice(0, indiceInteiro).map((parte) => parte.value).join('');
  const grupos: string[] = [];
  let grupoAtual = '';

  for (const parte of partes.slice(indiceInteiro)) {
    if (parte.type === 'group') {
      grupoAtual += parte.value;
      grupos.push(grupoAtual);
      grupoAtual = '';
    } else {
      grupoAtual += parte.value;
    }
  }

  if (grupoAtual) {
    grupos.push(grupoAtual);
  }

  return [prefixo, ...grupos];
}

export interface IndicadorDashboard {
  readonly chave: keyof DashboardAdministrador;
  readonly rotulo: string;
  readonly icone: string;
  readonly formato: 'quantidade' | 'moeda';
}

export const INDICADORES_DASHBOARD: readonly IndicadorDashboard[] = [
  {
    chave: 'totalQuotes',
    rotulo: 'Total de Orçamentos',
    icone: 'description',
    formato: 'quantidade',
  },
  {
    chave: 'pendingAcceptance',
    rotulo: 'Pendentes de Aceite',
    icone: 'show_chart',
    formato: 'quantidade',
  },
  {
    chave: 'activeClients',
    rotulo: 'Clientes Ativos',
    icone: 'groups',
    formato: 'quantidade',
  },
  {
    chave: 'totalQuotedValue',
    rotulo: 'Valor Total Orçado',
    icone: 'show_chart',
    formato: 'moeda',
  },
];

export interface AtalhoDashboard {
  readonly rotulo: string;
  readonly descricao: string;
  readonly icone: string;
  readonly rota: string;
}

export const ATALHOS_DASHBOARD: readonly AtalhoDashboard[] = [
  {
    rotulo: 'Orçamentos',
    descricao: 'Consulte e gerencie os orçamentos.',
    icone: 'description',
    rota: '/admin/orcamentos',
  },
  {
    rotulo: 'Técnicos',
    descricao: 'Consulte e gerencie os técnicos do laboratório.',
    icone: 'engineering',
    rota: '/admin/tecnicos',
  },
  {
    rotulo: 'Empresas',
    descricao: 'Consulte e gerencie as empresas cadastradas.',
    icone: 'business',
    rota: '/admin/empresas',
  },
  {
    rotulo: 'Clientes',
    descricao: 'Consulte e gerencie os clientes do portal.',
    icone: 'groups',
    rota: '/admin/clientes',
  },
];
