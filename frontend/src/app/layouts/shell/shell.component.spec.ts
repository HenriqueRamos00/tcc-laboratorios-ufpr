import { BreakpointObserver, BreakpointState } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import type { TourStep } from '@/app/model/tour';
import { AcceptanceDraftService } from '@/app/services/acceptance-draft.service';
import { LoginService } from '@/app/services/login.service';
import { TourService } from '@/app/services/tour.service';
import { ate, balao, botao, mostrando, tituloDoBalao } from '@/app/services/tour.spec-util';

import { ShellComponent } from './shell.component';
import { menuTour } from './shell.tour';

function montarShell(celular = false) {
  const tela = new BehaviorSubject<BreakpointState>({ matches: celular, breakpoints: {} });
  const logout = jasmine.createSpy('logout');

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: BreakpointObserver,
        useValue: { isMatched: () => tela.value.matches, observe: () => tela.asObservable() },
      },
      { provide: LoginService, useValue: { currentIdentity: () => null, logout } },
      {
        provide: AcceptanceDraftService,
        useValue: { clear: () => undefined, keepOnlyValidDraftsOf: () => undefined },
      },
    ],
  });

  const fixture = TestBed.createComponent(ShellComponent);
  fixture.autoDetectChanges();
  return { fixture, tela, logout, tour: TestBed.inject(TourService) };
}

function noShell(fixture: ComponentFixture<ShellComponent>, alvo: string): HTMLElement[] {
  return Array.from(fixture.nativeElement.querySelectorAll(`[data-tour="${alvo}"]`));
}

function itensDaAjuda(): HTMLButtonElement[] {
  return Array.from(document.querySelectorAll<HTMLButtonElement>('#shell-help-options button'));
}

async function abrirAjuda(fixture: ComponentFixture<ShellComponent>): Promise<void> {
  noShell(fixture, 'menu-ajuda')[0].click();
  await ate(() => itensDaAjuda().length === 2, 'opções da Ajuda abertas');
}

async function avancarAte(titulo: string): Promise<void> {
  for (let passos = 0; passos < 10 && tituloDoBalao() !== titulo; passos++) {
    const anterior = tituloDoBalao();
    botao('driver-popover-next-btn').click();
    await ate(() => tituloDoBalao() !== anterior, `passo seguinte a "${anterior}"`);
  }
  expect(tituloDoBalao()).toBe(titulo);
}

describe('ShellComponent, ajuda guiada', () => {
  let tour: TourService | undefined;

  afterEach(async () => {
    tour?.stop();
    await ate(() => balao() === null, 'balão removido ao encerrar');
  });

  it('o botão Ajuda oferece o tour do menu e o da página', async () => {
    const shell = montarShell();
    tour = shell.tour;

    const ajuda = noShell(shell.fixture, 'menu-ajuda')[0];
    expect(ajuda.getAttribute('aria-expanded')).toBe('false');
    await abrirAjuda(shell.fixture);
    expect(ajuda.getAttribute('aria-expanded')).toBe('true');
    expect(ajuda.closest('mat-sidenav')?.querySelector('#shell-help-options')).not.toBeNull();

    const [sobreOMenu, sobreAPagina] = itensDaAjuda();
    expect(sobreOMenu.textContent).toContain('Sobre o menu');
    expect(sobreAPagina.textContent).toContain('Sobre esta página');
    expect(sobreAPagina.disabled).toBeTrue();
    sobreOMenu.click();
    await mostrando('Orçamentos');
  });

  it('"Sobre esta página" abre o tour que a página registrou', async () => {
    const shell = montarShell();
    tour = shell.tour;
    const passoDaPagina: TourStep = {
      id: 'ajuda',
      target: 'menu-ajuda',
      title: 'Passo da página',
      text: 'Texto',
    };
    tour.registerPage(() => ({ id: 'pagina', label: 'Sobre esta página', steps: [passoDaPagina] }));
    await shell.fixture.whenStable();

    await abrirAjuda(shell.fixture);
    const [, sobreAPagina] = itensDaAjuda();
    expect(sobreAPagina.disabled).toBeFalse();
    sobreAPagina.click();

    await mostrando('Passo da página');
  });

  it('"Sobre o menu" passa por cada página, pelas opções de usuário e pelo sair', async () => {
    const shell = montarShell();
    tour = shell.tour;

    await tour.start(menuTour);
    await mostrando('Orçamentos');
    expect(noShell(shell.fixture, 'menu-orcamentos')[0].classList).toContain('driver-active-element');
    expect(balao()!.textContent).toContain('Esta é a página de Orçamentos');

    await avancarAte('Relatórios');
    await avancarAte('Equipamentos');
    await avancarAte('Ajuda');
    await avancarAte('Opções de usuário');
    await avancarAte('Recolher o menu');
    await avancarAte('Sair');
    expect(botao('driver-popover-next-btn').textContent).toBe('Concluir');
  });

  it('o sair não funciona enquanto o tour o destaca, e volta a funcionar depois', async () => {
    const shell = montarShell();
    tour = shell.tour;

    await tour.start(menuTour);
    await mostrando('Orçamentos');
    await avancarAte('Sair');

    const sair = noShell(shell.fixture, 'menu-sair')[0];
    expect(sair.classList).toContain('driver-active-element');
    expect(getComputedStyle(sair).pointerEvents).toBe('none');
    sair.click();
    shell.fixture.componentInstance.logout();
    expect(shell.logout).not.toHaveBeenCalled();

    tour.stop();
    sair.click();
    expect(shell.logout).toHaveBeenCalledTimes(1);
  });

  it('recolher o menu também fica travado durante o tour', async () => {
    const shell = montarShell();
    tour = shell.tour;
    const recolhidoAntes = shell.fixture.componentInstance.collapsed();

    await tour.start(menuTour);
    await mostrando('Orçamentos');
    noShell(shell.fixture, 'menu-recolher')[0].click();

    expect(shell.fixture.componentInstance.collapsed()).toBe(recolhidoAntes);
  });

  it('no celular abre a gaveta antes de destacar os itens do menu', async () => {
    const shell = montarShell(true);
    tour = shell.tour;
    const gaveta: HTMLElement = shell.fixture.nativeElement.querySelector('mat-sidenav');
    expect(gaveta.classList).not.toContain('mat-drawer-opened');

    await tour.start(menuTour);
    await mostrando('Orçamentos');

    expect(gaveta.classList).toContain('mat-drawer-opened');
    expect(noShell(shell.fixture, 'menu-orcamentos')[0].classList).toContain('driver-active-element');

    await avancarAte('Sair');
    expect(noShell(shell.fixture, 'menu-recolher').length).toBe(0);
    expect(noShell(shell.fixture, 'menu-sair')[0].closest('mat-sidenav')).toBe(gaveta);
  });

  it('cruzar o breakpoint no meio do tour mantém o passo no alvo do novo layout', async () => {
    const shell = montarShell();
    tour = shell.tour;
    const gaveta: HTMLElement = shell.fixture.nativeElement.querySelector('mat-sidenav');

    await tour.start(menuTour);
    await mostrando('Orçamentos');
    await avancarAte('Sair');
    expect(noShell(shell.fixture, 'menu-sair')[0].closest('mat-sidenav')).toBeNull();

    shell.tela.next({ matches: true, breakpoints: {} });

    await ate(() => {
      const sair = noShell(shell.fixture, 'menu-sair')[0];
      return !!sair && sair.closest('mat-sidenav') === gaveta && sair.classList.contains('driver-active-element');
    }, 'destaque no sair da gaveta');
    expect(gaveta.classList).toContain('mat-drawer-opened');
    expect(tituloDoBalao()).toBe('Sair');
    expect(tour.active()).toBeTrue();

    shell.tela.next({ matches: false, breakpoints: {} });

    await ate(() => {
      const sair = noShell(shell.fixture, 'menu-sair')[0];
      return !!sair && sair.closest('mat-sidenav') === null && sair.classList.contains('driver-active-element');
    }, 'destaque de volta no sair do cabeçalho');
    expect(tituloDoBalao()).toBe('Sair');
  });

  it('no celular fecha a gaveta para os passos da página', async () => {
    const shell = montarShell(true);
    tour = shell.tour;
    const gaveta: HTMLElement = shell.fixture.nativeElement.querySelector('mat-sidenav');
    const menuDoCabecalho: HTMLElement = shell.fixture.nativeElement.querySelector('header button');
    menuDoCabecalho.setAttribute('data-tour', 'alvo-da-pagina');
    menuDoCabecalho.click();
    await ate(() => gaveta.classList.contains('mat-drawer-opened'), 'gaveta aberta pelo usuário');

    await tour.start(() => ({
      id: 'pagina',
      label: 'Sobre esta página',
      steps: [{ id: 'alvo', target: 'alvo-da-pagina', title: 'Na página', text: 'Texto' }],
    }));
    await mostrando('Na página');

    expect(gaveta.classList).not.toContain('mat-drawer-opened');
  });
});
