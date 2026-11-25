import type { TourDefinition } from '@/app/model/tour';
import { paginationTourSteps } from '@shared/components/pagination/pagination.tour';

export function quoteReportsTour(): TourDefinition {
  return {
    id: 'relatorios-do-orcamento',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'progresso',
        target: 'laudos-progresso',
        side: 'bottom',
        title: 'Progresso da solicitação',
        text: 'A linha mostra em que etapa este orçamento está, da proposta até a publicação do relatório.',
      },
      {
        id: 'resumo',
        target: 'laudos-resumo',
        side: 'bottom',
        title: 'Resumo da análise',
        text: 'Aqui ficam o tipo de ensaio realizado e o resumo da conclusão do laboratório.',
      },
      {
        id: 'sem-relatorio',
        target: 'laudos-sem-relatorio',
        side: 'top',
        title: 'Ainda sem relatório',
        text: 'Este orçamento ainda não tem laudo publicado. Assim que o laboratório publicar, a lista de relatórios aparece neste espaço.',
      },
      {
        id: 'busca',
        target: 'laudos-busca',
        side: 'bottom',
        title: 'Buscar entre os relatórios',
        text: 'Procure pelo número do relatório ou da solicitação quando o orçamento tiver vários laudos.',
      },
      {
        id: 'tabela',
        target: 'laudos-tabela',
        side: 'top',
        title: 'Relatórios deste orçamento',
        text: 'Cada linha é um laudo deste orçamento, com solicitação, amostra, laboratório, situação e data. O botão de download baixa o arquivo.',
      },
      ...paginationTourSteps(),
    ],
  };
}
