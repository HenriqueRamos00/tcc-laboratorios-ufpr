import type { TourDefinition } from '@/app/model/tour';
import { paginationTourSteps } from '@shared/components/pagination/pagination.tour';

export function equipmentDetailTour(): TourDefinition {
  return {
    id: 'equipamento',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'identificacao',
        target: 'equipamento-identificacao',
        side: 'bottom',
        title: 'Identificação',
        text: 'Estes são os dados de placa do equipamento. Um campo sem valor indica que a informação ainda não foi recebida pelo portal.',
      },
      {
        id: 'indicadores',
        target: 'equipamento-indicadores',
        side: 'bottom',
        title: 'Ver Indicadores de Saúde',
        text: 'Abre os indicadores calculados com as análises mais recentes deste equipamento: conformidade, evolução dos ensaios e conclusão do laboratório.',
      },
      {
        id: 'historico',
        target: 'equipamento-historico',
        side: 'top',
        title: 'Histórico de Relatórios',
        text: 'Cada serviço realizado com o Lactec entra neste histórico, do mais recente ao mais antigo.',
      },
      {
        id: 'historico-indicadores',
        target: 'equipamento-historico-indicadores',
        side: 'left',
        title: 'Ver Indicadores',
        text: 'Mostra os indicadores de saúde como estavam naquela análise específica.',
      },
      {
        id: 'historico-visualizar',
        target: 'equipamento-historico-visualizar',
        side: 'left',
        title: 'Visualizar',
        text: 'Abre o relatório daquela análise em tela cheia, sem baixar o arquivo. No celular dá para ampliar com dois dedos.',
      },
      {
        id: 'historico-download',
        target: 'equipamento-historico-download',
        side: 'left',
        title: 'Download',
        text: 'Baixa o relatório daquela análise. O botão fica desativado quando o arquivo ainda não está disponível.',
      },
      ...paginationTourSteps(),
    ],
  };
}
