import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';

import type { TourDefinition, TourScene, TourStep } from '@/app/model/tour';

import { TourService } from './tour.service';
import { ate, balao, botao, mostrando, progressoDoBalao, tituloDoBalao } from './tour.spec-util';

@Component({
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" data-tour="primeiro" (click)="cliques.set(cliques() + 1)">Primeiro</button>
    @if (mostrarSegundo()) {
      <button type="button" data-tour="segundo">Segundo</button>
    }
    <span data-tour="alternado" [style.display]="largo() ? 'inline-block' : 'none'" id="largo">
      Largo
    </span>
    <span data-tour="alternado" [style.display]="largo() ? 'none' : 'inline-block'" id="estreito">
      Estreito
    </span>
    <button type="button" data-tour="menu-item" style="visibility: hidden">No menu</button>
    <div data-area-rolavel style="height: 120px; overflow: auto">
      <div style="height: 900px"></div>
      <button type="button" data-tour="la-embaixo">Lá embaixo</button>
      <div style="height: 900px"></div>
    </div>
    @if (cenaAberta()) {
      <p data-tour="na-cena">Dentro da cena</p>
    }
  `,
})
class TelaDeTeste {
  readonly cliques = signal(0);
  readonly mostrarSegundo = signal(true);
  readonly largo = signal(true);
  readonly cenaAberta = signal(false);
}

function passo(target: string, extra: Partial<TourStep> = {}): TourStep {
  return { id: target, target, title: `Título de ${target}`, text: `Texto de ${target}`, ...extra };
}

function roteiro(...steps: TourStep[]): () => TourDefinition {
  return () => ({ id: 'teste', label: 'Teste', steps });
}

describe('Serviço de ajuda guiada', () => {
  let fixture: ComponentFixture<TelaDeTeste>;
  let tour: TourService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    fixture = TestBed.createComponent(TelaDeTeste);
    fixture.autoDetectChanges();
    tour = TestBed.inject(TourService);
  });

  afterEach(async () => {
    tour.stop();
    await ate(() => balao() === null, 'balão removido ao encerrar');
  });

  function alvo(seletor: string): HTMLElement {
    return fixture.nativeElement.querySelector(seletor);
  }

  it('destaca o primeiro passo em um diálogo nomeado, com o progresso', async () => {
    await tour.start(roteiro(passo('primeiro'), passo('segundo')));
    await mostrando('Título de primeiro');

    const dialogo = balao()!;
    expect(tour.active()).toBeTrue();
    expect(dialogo.getAttribute('role')).toBe('dialog');
    expect(dialogo.getAttribute('aria-labelledby')).toBeTruthy();
    expect(dialogo.getAttribute('aria-describedby')).toBeTruthy();
    expect(dialogo.classList).toContain('lactec-tour');
    expect(dialogo.textContent).toContain('Texto de primeiro');
    expect(progressoDoBalao()).toBe('Passo 1 de 2');
    expect(botao('driver-popover-prev-btn').classList).toContain('driver-popover-btn-disabled');
    expect(botao('driver-popover-close-btn').getAttribute('aria-label')).toBe('Fechar ajuda');
  });

  it('leva o foco para dentro do balão', async () => {
    await tour.start(roteiro(passo('primeiro'), passo('segundo')));
    await mostrando('Título de primeiro');

    await ate(() => balao()!.contains(document.activeElement), 'foco dentro do balão');
    expect(balao()!.contains(document.activeElement)).toBeTrue();
  });

  it('libera a barra fixa do formulário enquanto a ajuda está aberta', async () => {
    const classeDaAjudaDoFormulario = 'tour-formulario-ensaio';
    await tour.start(() => ({
      id: 'formulario-ensaio',
      label: 'Sobre esta página',
      steps: [passo('primeiro'), passo('segundo')],
    }));
    await mostrando('Título de primeiro');

    expect(document.body.classList).toContain(classeDaAjudaDoFormulario);

    botao('driver-popover-next-btn').click();
    await mostrando('Título de segundo');
    expect(document.body.classList).toContain(classeDaAjudaDoFormulario);

    tour.stop();
    expect(document.body.classList).not.toContain(classeDaAjudaDoFormulario);
  });

  it('o alvo destacado não responde a clique enquanto o tour explica', async () => {
    await tour.start(roteiro(passo('primeiro')));
    await mostrando('Título de primeiro');

    const destacado = alvo('[data-tour="primeiro"]');
    expect(destacado.classList).toContain('driver-no-interaction');
    expect(getComputedStyle(destacado).pointerEvents).toBe('none');
  });

  it('avança pelo botão e pela seta, e conclui no último passo', async () => {
    await tour.start(roteiro(passo('primeiro'), passo('segundo'), passo('la-embaixo')));
    await mostrando('Título de primeiro');

    botao('driver-popover-next-btn').click();
    await mostrando('Título de segundo');
    expect(progressoDoBalao()).toBe('Passo 2 de 3');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    await mostrando('Título de la-embaixo');
    expect(botao('driver-popover-next-btn').textContent).toBe('Concluir');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
    await mostrando('Título de segundo');

    botao('driver-popover-next-btn').click();
    await mostrando('Título de la-embaixo');
    botao('driver-popover-next-btn').click();

    await ate(() => !tour.active(), 'tour encerrado ao concluir');
  });

  it('Esc encerra o tour', async () => {
    await tour.start(roteiro(passo('primeiro'), passo('segundo')));
    await mostrando('Título de primeiro');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    document.dispatchEvent(new KeyboardEvent('keyup', { key: 'Escape', bubbles: true }));

    await ate(() => !tour.active(), 'tour encerrado com Esc');
    expect(tour.active()).toBeFalse();
  });

  it('o botão de fechar encerra o tour', async () => {
    await tour.start(roteiro(passo('primeiro')));
    await mostrando('Título de primeiro');

    botao('driver-popover-close-btn').click();

    await ate(() => !tour.active(), 'tour encerrado pelo fechar');
    expect(tour.active()).toBeFalse();
  });

  it('pula o passo cujo alvo não está na tela e não o conta no progresso', async () => {
    fixture.componentInstance.mostrarSegundo.set(false);
    await fixture.whenStable();

    await tour.start(roteiro(passo('ausente'), passo('primeiro'), passo('segundo'), passo('la-embaixo')));
    await mostrando('Título de primeiro');
    expect(progressoDoBalao()).toBe('Passo 1 de 2');

    botao('driver-popover-next-btn').click();
    await mostrando('Título de la-embaixo');
    expect(progressoDoBalao()).toBe('Passo 2 de 2');
  });

  it('avisa quando não há nada para destacar, em vez de travar', async () => {
    await tour.start(roteiro(passo('ausente'), passo('tambem-ausente')));
    await mostrando('Nada para destacar agora');

    botao('driver-popover-next-btn').click();

    await ate(() => !tour.active(), 'tour encerrado pelo aviso');
    expect(tour.active()).toBeFalse();
  });

  it('rola a área rolável até o alvo que está fora da vista', async () => {
    const area = alvo('[data-area-rolavel]');
    expect(area.scrollTop).toBe(0);

    await tour.start(roteiro(passo('la-embaixo')));
    await mostrando('Título de la-embaixo');

    const caixa = area.getBoundingClientRect();
    const destacado = alvo('[data-tour="la-embaixo"]').getBoundingClientRect();
    expect(area.scrollTop).toBeGreaterThan(0);
    expect(destacado.top).toBeGreaterThanOrEqual(caixa.top);
    expect(destacado.bottom).toBeLessThanOrEqual(caixa.bottom);
  });

  it('escolhe, entre alvos de mesmo nome, o que está visível, e troca quando o layout muda', async () => {
    await tour.start(roteiro(passo('alternado'), passo('primeiro')));
    await mostrando('Título de alternado');
    expect(alvo('#largo').classList).toContain('driver-active-element');

    fixture.componentInstance.largo.set(false);
    tour.layoutChanged();

    await ate(
      () => alvo('#estreito').classList.contains('driver-active-element'),
      'destaque no alvo do layout estreito',
    );
    expect(alvo('#largo').classList).not.toContain('driver-active-element');
    expect(tituloDoBalao()).toBe('Título de alternado');
  });

  it('segue para o próximo passo quando o alvo atual some da tela', async () => {
    await tour.start(roteiro(passo('segundo'), passo('la-embaixo')));
    await mostrando('Título de segundo');

    fixture.componentInstance.mostrarSegundo.set(false);

    await mostrando('Título de la-embaixo');
    expect(progressoDoBalao()).toBe('Passo 1 de 1');
  });

  it('sobrevive a redimensionamentos seguidos da janela', async () => {
    await tour.start(roteiro(passo('primeiro'), passo('segundo')));
    await mostrando('Título de primeiro');

    for (let vez = 0; vez < 5; vez++) window.dispatchEvent(new Event('resize'));
    await new Promise((resolve) => setTimeout(resolve, 300));

    expect(tour.active()).toBeTrue();
    expect(tituloDoBalao()).toBe('Título de primeiro');
    expect(alvo('[data-tour="primeiro"]').classList).toContain('driver-active-element');
  });

  it('entra na cena antes do passo e sai dela ao mudar de cena e ao encerrar', async () => {
    const tela = fixture.componentInstance;
    const eventos: string[] = [];
    const cena: TourScene = {
      enter: () => {
        eventos.push('entrou');
        setTimeout(() => tela.cenaAberta.set(true), 80);
      },
      leave: () => {
        eventos.push('saiu');
        tela.cenaAberta.set(false);
      },
    };

    await tour.start(
      roteiro(passo('primeiro'), passo('na-cena', { scene: cena }), passo('segundo')),
    );
    await mostrando('Título de primeiro');
    expect(progressoDoBalao()).toBe('Passo 1 de 3');

    botao('driver-popover-next-btn').click();
    await mostrando('Título de na-cena');
    expect(eventos).toEqual(['entrou']);

    botao('driver-popover-next-btn').click();
    await mostrando('Título de segundo');
    expect(eventos).toEqual(['entrou', 'saiu']);

    botao('driver-popover-prev-btn').click();
    await mostrando('Título de na-cena');
    tour.stop();

    expect(eventos).toEqual(['entrou', 'saiu', 'entrou', 'saiu']);
  });

  it('no celular abre o menu para os passos do menu e o fecha para os da página', async () => {
    const chamadas: string[] = [];
    const itemDoMenu = alvo('[data-tour="menu-item"]');
    tour.registerHost({
      isHandset: () => true,
      openMenu: async () => {
        chamadas.push('abrir');
        itemDoMenu.style.visibility = 'visible';
      },
      closeMenu: async () => {
        chamadas.push('fechar');
        itemDoMenu.style.visibility = 'hidden';
      },
    });

    await tour.start(roteiro(passo('menu-item', { region: 'menu' }), passo('primeiro')));
    await mostrando('Título de menu-item');
    expect(chamadas).toEqual(['abrir']);
    expect(progressoDoBalao()).toBe('Passo 1 de 2');

    botao('driver-popover-next-btn').click();
    await mostrando('Título de primeiro');
    expect(chamadas).toEqual(['abrir', 'fechar']);
  });

  it('no desktop não mexe no menu', async () => {
    const chamadas: string[] = [];
    alvo('[data-tour="menu-item"]').style.visibility = 'visible';
    tour.registerHost({
      isHandset: () => false,
      openMenu: async () => void chamadas.push('abrir'),
      closeMenu: async () => void chamadas.push('fechar'),
    });

    await tour.start(roteiro(passo('menu-item', { region: 'menu' }), passo('primeiro')));
    await mostrando('Título de menu-item');

    expect(chamadas).toEqual([]);
  });

  it('encerra quando a rota muda', async () => {
    await tour.start(roteiro(passo('primeiro')));
    await mostrando('Título de primeiro');

    await TestBed.inject(Router).navigateByUrl('/');

    expect(tour.active()).toBeFalse();
  });

  it('o tour da página só existe enquanto a página o registra', async () => {
    expect(tour.hasPageTour()).toBeFalse();

    const remover = tour.registerPage(roteiro(passo('primeiro')));
    expect(tour.hasPageTour()).toBeTrue();

    await tour.startPage();
    await mostrando('Título de primeiro');

    remover();
    expect(tour.hasPageTour()).toBeFalse();
    expect(tour.active()).toBeFalse();
  });

  it('respeita a preferência por menos movimento', async () => {
    const original = window.matchMedia;
    spyOn(window, 'matchMedia').and.callFake((consulta: string) =>
      consulta.includes('prefers-reduced-motion')
        ? ({ matches: true } as MediaQueryList)
        : original.call(window, consulta),
    );

    await tour.start(roteiro(passo('primeiro')));
    await mostrando('Título de primeiro');

    expect(document.body.classList).not.toContain('driver-fade');
  });
});
