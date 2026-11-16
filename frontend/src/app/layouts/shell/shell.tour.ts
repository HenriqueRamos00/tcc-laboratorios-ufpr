import type { TourDefinition } from '@/app/model/tour';

export const MENU_TOUR_TARGET = {
  quotes: 'menu-orcamentos',
  reports: 'menu-relatorios',
  equipment: 'menu-equipamentos',
  help: 'menu-ajuda',
  profile: 'menu-perfil',
  logout: 'menu-sair',
  collapse: 'menu-recolher',
} as const;

export function menuTour(): TourDefinition {
  return {
    id: 'menu',
    label: 'Sobre o menu',
    steps: [
      {
        id: 'menu-orcamentos',
        target: MENU_TOUR_TARGET.quotes,
        region: 'menu',
        side: 'right',
        title: 'Orçamentos',
        text: 'Esta é a página de Orçamentos. Aqui você encontra todas as propostas do Lactec para a sua empresa, acompanha a situação de cada uma e aprova ou recusa as que aguardam a sua decisão.',
      },
      {
        id: 'menu-relatorios',
        target: MENU_TOUR_TARGET.reports,
        region: 'menu',
        side: 'right',
        title: 'Relatórios',
        text: 'Esta é a página de Relatórios. Aqui você pode consultar e baixar os laudos já publicados, buscando entre todos eles ou entrando pelo orçamento que os gerou.',
      },
      {
        id: 'menu-equipamentos',
        target: MENU_TOUR_TARGET.equipment,
        region: 'menu',
        side: 'right',
        title: 'Equipamentos',
        text: 'Esta é a página de Equipamentos. Você pode acompanhar a saúde de um equipamento conforme realizar serviços com o Lactec: cada análise entra no histórico e alimenta os indicadores.',
      },
      {
        id: 'menu-ajuda',
        target: MENU_TOUR_TARGET.help,
        region: 'menu',
        side: 'right',
        title: 'Ajuda',
        text: 'Sempre que precisar, abra a Ajuda. "Sobre o menu" repete esta apresentação e "Sobre esta página" explica a tela que estiver aberta.',
      },
      {
        id: 'menu-perfil',
        target: MENU_TOUR_TARGET.profile,
        region: 'menu',
        side: 'right',
        title: 'Opções de usuário',
        text: 'Aqui ficam as opções da sua conta, como o Perfil.',
      },
      {
        id: 'menu-recolher',
        target: MENU_TOUR_TARGET.collapse,
        region: 'menu',
        side: 'right',
        title: 'Recolher o menu',
        text: 'Recolha o menu para ganhar espaço na tela. Os ícones continuam visíveis e o portal lembra da sua escolha.',
      },
      {
        id: 'menu-sair',
        target: MENU_TOUR_TARGET.logout,
        region: 'menu',
        side: 'bottom',
        title: 'Sair',
        text: 'Este botão encerra a sua sessão no portal. Use-o ao terminar, principalmente em computador compartilhado. Durante esta apresentação ele fica desativado.',
      },
    ],
  };
}
