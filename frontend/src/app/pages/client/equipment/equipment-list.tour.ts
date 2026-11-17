import type { TourDefinition } from '@/app/model/tour';
import { paginationTourSteps } from '@shared/components/pagination/pagination.tour';

export function equipmentListTour(): TourDefinition {
  return {
    id: 'equipamentos',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'busca',
        target: 'equipamentos-busca',
        side: 'bottom',
        title: 'Buscar um equipamento',
        text: 'Procure pelo número de série ou pela TAG do equipamento.',
      },
      {
        id: 'tabela',
        target: 'equipamentos-tabela',
        side: 'top',
        title: 'Equipamentos da sua empresa',
        text: 'A lista traz número de série, TAG, tipo e quantos laudos cada equipamento já tem.',
      },
      {
        id: 'detalhes',
        target: 'equipamentos-detalhes',
        side: 'left',
        title: 'Ver Detalhes',
        text: 'Abre a ficha do equipamento, com os dados de identificação, o histórico de relatórios e o acesso aos indicadores de saúde.',
      },
      ...paginationTourSteps(),
    ],
  };
}
