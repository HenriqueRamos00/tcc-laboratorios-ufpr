import { TestBed } from '@angular/core/testing';

import { PaginationComponent } from './pagination.component';
import { PAGINATION_TOUR_TARGET, paginationTourSteps } from './pagination.tour';

function render(pageSizeOptions: { value: string; label: string }[]) {
  const fixture = TestBed.createComponent(PaginationComponent);
  fixture.componentRef.setInput('page', 1);
  fixture.componentRef.setInput('pageSize', 8);
  fixture.componentRef.setInput('total', 952);
  fixture.componentRef.setInput('pageSizeOptions', pageSizeOptions);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('PaginationComponent, alvos da ajuda guiada', () => {
  it('marca o seletor de linhas por página e a navegação entre páginas', () => {
    const screen = render([{ value: 'auto', label: 'Auto (8)' }]);

    const pageSize = screen.querySelector(`[data-tour="${PAGINATION_TOUR_TARGET.pageSize}"]`);
    const pages = screen.querySelector(`[data-tour="${PAGINATION_TOUR_TARGET.pages}"]`);
    expect(pageSize?.querySelector('select')).not.toBeNull();
    expect(pages?.textContent).toContain('1-8 de 952');
    expect(pages?.textContent).toContain('Anterior');
    expect(pages?.textContent).toContain('Próximo');
  });

  it('sem opções de tamanho não há alvo para esse passo, e o tour o pula', () => {
    const screen = render([]);

    expect(screen.querySelector(`[data-tour="${PAGINATION_TOUR_TARGET.pageSize}"]`)).toBeNull();
    expect(screen.querySelector(`[data-tour="${PAGINATION_TOUR_TARGET.pages}"]`)).not.toBeNull();
  });

  it('os passos apontam para esses alvos, e a tela sem seletor de tamanho fica só com a navegação', () => {
    expect(paginationTourSteps().map((step) => step.target)).toEqual([
      PAGINATION_TOUR_TARGET.pageSize,
      PAGINATION_TOUR_TARGET.pages,
    ]);
    expect(paginationTourSteps({ pageSize: false }).map((step) => step.target)).toEqual([
      PAGINATION_TOUR_TARGET.pages,
    ]);
  });
});
