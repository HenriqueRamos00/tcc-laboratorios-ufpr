import type { TourStep } from '@/app/model/tour';

export const PAGINATION_TOUR_TARGET = {
  pageSize: 'paginacao-tamanho',
  pages: 'paginacao-paginas',
} as const;

export function paginationTourSteps(options: { readonly pageSize?: boolean } = {}): TourStep[] {
  const steps: TourStep[] = [
    {
      id: 'paginacao-tamanho',
      target: PAGINATION_TOUR_TARGET.pageSize,
      side: 'top',
      title: 'Quantos itens por página',
      text: 'Escolha quantas linhas aparecem de uma vez. Em "Auto", a lista mostra quantas cabem na sua tela, sem precisar rolar; o número entre parênteses é essa quantidade. Escolha 10, 25, 50 ou 100 para fixar outro tamanho.',
    },
    {
      id: 'paginacao-paginas',
      target: PAGINATION_TOUR_TARGET.pages,
      side: 'top',
      title: 'Andar entre as páginas',
      text: 'Aqui você vê quais itens estão na tela e o total encontrado. Quando houver mais resultados do que cabem em uma página, use Próximo e Anterior para navegar entre elas.',
    },
  ];
  return options.pageSize === false ? steps.filter((step) => step.id !== 'paginacao-tamanho') : steps;
}
