import type { TourDefinition, TourScene } from '@/app/model/tour';
import { paginationTourSteps } from '@shared/components/pagination/pagination.tour';

export function quoteListTour(statusDialog: TourScene): TourDefinition {
  return {
    id: 'orcamentos',
    label: 'Sobre esta página',
    steps: [
      {
        id: 'filtros',
        target: 'orcamentos-filtros',
        side: 'bottom',
        title: 'Filtrar por situação',
        text: 'Escolha entre Todos, Pendentes de aceite, Em Andamento, Finalizados e Cancelados para ver só os orçamentos daquela situação. Em telas estreitas, as mesmas opções ficam reunidas em um seletor.',
      },
      {
        id: 'busca',
        target: 'orcamentos-busca',
        side: 'bottom',
        title: 'Pesquisar',
        text: 'Digite o ID da proposta, o nome, a empresa ou o contato para encontrar um orçamento entre os que estão na página.',
      },
      {
        id: 'cartao',
        target: 'orcamento-cartao',
        title: 'Um orçamento',
        text: 'Cada cartão é um orçamento, com o ID da proposta, a empresa, o contato, a data de criação e, quando já existe, o valor estimado.',
      },
      {
        id: 'status',
        target: 'orcamento-status',
        side: 'left',
        title: 'Etiqueta de status',
        text: 'A etiqueta mostra em que etapa o orçamento está, da qualificação do pedido até o relatório publicado. A cor muda junto com a etapa.',
      },
      {
        id: 'verificar-status',
        target: 'orcamento-verificar-status',
        side: 'left',
        title: 'Verificar Status',
        text: 'Abre o andamento completo do orçamento. Quando o relatório já foi publicado, o botão leva direto aos relatórios. Veja a seguir o que essa janela mostra.',
      },
      {
        id: 'status-etapas',
        target: 'status-etapas',
        region: 'dialog',
        scene: statusDialog,
        side: 'bottom',
        title: 'Linha de etapas',
        text: 'A janela de status mostra a linha de etapas do orçamento: as concluídas, a atual em destaque e as que ainda vêm pela frente.',
      },
      {
        id: 'status-mensagem',
        target: 'status-mensagem',
        region: 'dialog',
        scene: statusDialog,
        title: 'O que acontece agora',
        text: 'Este aviso explica a etapa atual e diz se o Lactec espera alguma ação sua, como aprovar a proposta ou enviar a amostra.',
      },
      {
        id: 'status-acoes',
        target: 'status-acoes',
        region: 'dialog',
        scene: statusDialog,
        side: 'top',
        title: 'Baixar e falar com o laboratório',
        text: 'Quando a proposta já existe, você pode baixá-la em PDF por aqui. Depois que a amostra chega, a janela também mostra o contato do laboratório.',
      },
      {
        id: 'visualizar',
        target: 'orcamento-visualizar',
        side: 'left',
        title: 'Visualizar Orçamento',
        text: 'Abre a proposta completa, onde você lê o documento e pode aceitar ou recusar. O botão só aparece depois que a proposta foi escrita.',
      },
      {
        id: 'paginacao',
        target: 'orcamentos-paginacao',
        side: 'top',
        title: 'Mais orçamentos',
        text: 'Os orçamentos são carregados por página. Avance ou volte por aqui para ver os demais.',
      },
      ...paginationTourSteps({ pageSize: false }),
    ],
  };
}
