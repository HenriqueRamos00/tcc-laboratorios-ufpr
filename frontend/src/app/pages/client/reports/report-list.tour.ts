import type { TourDefinition } from '@/app/model/tour';
import { paginationTourSteps } from '@shared/components/pagination/pagination.tour';

export function reportListTour(): TourDefinition {
  return {
    id: 'relatorios',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'abas',
        target: 'relatorios-abas',
        side: 'bottom',
        title: 'Duas formas de encontrar um laudo',
        text: '"Todos os Relatórios" lista cada laudo publicado. "Orçamentos com Relatório" agrupa os laudos pelo orçamento que os gerou. Esta ajuda explica a aba que estiver aberta.',
      },
      {
        id: 'busca',
        target: 'relatorios-busca',
        side: 'bottom',
        title: 'Buscar um laudo',
        text: 'Procure pelo número do relatório, da solicitação ou do orçamento.',
      },
      {
        id: 'tabela',
        target: 'relatorios-tabela',
        side: 'top',
        title: 'Laudos publicados',
        text: 'Cada linha é um laudo, com solicitação, amostra, laboratório, situação e data de publicação. O botão de download baixa o arquivo do laudo.',
      },
      {
        id: 'orcamentos-busca',
        target: 'relatorios-orcamentos-busca',
        side: 'bottom',
        title: 'Buscar um orçamento',
        text: 'Procure pelo ID da proposta, pelo nome ou pela empresa.',
      },
      {
        id: 'orcamento-cartao',
        target: 'relatorios-orcamento-cartao',
        title: 'Orçamento com relatório',
        text: 'Aqui aparecem apenas os orçamentos que já têm laudo publicado, com a etiqueta da etapa em que cada um está.',
      },
      {
        id: 'ver',
        target: 'relatorios-ver',
        side: 'left',
        title: 'Ver Relatórios',
        text: 'Abre os laudos deste orçamento, junto com o progresso da solicitação e o resumo da análise.',
      },
      ...paginationTourSteps(),
    ],
  };
}
