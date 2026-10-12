import { TestBed } from '@angular/core/testing';

import { ConformityBannerComponent } from './conformity-banner.component';

// A tarja existe para ser lida em cima de um fundo cheio. Os testes prendem as
// duas coisas que a fazem falhar calada: o rótulo errado e o par fundo/texto
// desemparelhado, que é como nasce texto branco sobre amarelo.
describe('ConformityBannerComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [ConformityBannerComponent] }));

  function renderizarCom(classification: 'normal' | 'alerta' | 'nao-conforme'): HTMLElement {
    const fixture = TestBed.createComponent(ConformityBannerComponent);
    fixture.componentRef.setInput('classification', classification);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('span') as HTMLElement;
  }

  it('escreve a classificação dentro da tarja', () => {
    expect(renderizarCom('normal').textContent?.trim()).toBe('Normal');
    expect(renderizarCom('nao-conforme').textContent?.trim()).toBe('Não conforme');
  });

  it('traz sempre um fundo e uma cor de texto escolhidos juntos', () => {
    for (const classification of ['normal', 'alerta', 'nao-conforme'] as const) {
      const classes = renderizarCom(classification).className;
      expect(classes).toMatch(/\bbg-lactec-/);
      expect(classes).toMatch(/\btext-lactec-/);
    }
  });

  it('não pinta texto claro sobre o amarelo de alerta', () => {
    expect(renderizarCom('alerta').className).not.toContain('text-lactec-on-primary');
  });
});
