import { TestBed } from '@angular/core/testing';

import { TarjaDeConformidadeComponent } from './tarja-de-conformidade.component';

// A tarja existe para ser lida em cima de um fundo cheio. Os testes prendem as
// duas coisas que a fazem falhar calada: o rótulo errado e o par fundo/texto
// desemparelhado, que é como nasce texto branco sobre amarelo.
describe('TarjaDeConformidadeComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [TarjaDeConformidadeComponent] }));

  function renderizarCom(classificacao: 'normal' | 'alerta' | 'nao-conforme'): HTMLElement {
    const fixture = TestBed.createComponent(TarjaDeConformidadeComponent);
    fixture.componentRef.setInput('classificacao', classificacao);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('span') as HTMLElement;
  }

  it('escreve a classificação dentro da tarja', () => {
    expect(renderizarCom('normal').textContent?.trim()).toBe('Normal');
    expect(renderizarCom('nao-conforme').textContent?.trim()).toBe('Não conforme');
  });

  it('traz sempre um fundo e uma cor de texto escolhidos juntos', () => {
    for (const classificacao of ['normal', 'alerta', 'nao-conforme'] as const) {
      const classes = renderizarCom(classificacao).className;
      expect(classes).toMatch(/\bbg-lactec-/);
      expect(classes).toMatch(/\btext-lactec-/);
    }
  });

  it('não pinta texto claro sobre o amarelo de alerta', () => {
    expect(renderizarCom('alerta').className).not.toContain('text-lactec-on-primary');
  });
});
