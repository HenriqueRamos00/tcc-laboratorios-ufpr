import { BreakpointObserver } from '@angular/cdk/layout';
import { Component } from '@angular/core';
import { fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';

import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

import { AppDialogService } from './app-dialog.service';

const TITULO = 'Status da proposta de teste';

@Component({
  standalone: true,
  imports: [DialogShellComponent],
  template: `
    <app-dialog-shell [title]="title">
      <button type="button">Primeiro botão do corpo</button>
    </app-dialog-shell>
  `,
})
class DialogoDeTeste {
  readonly title = TITULO;
}

@Component({ standalone: true, template: '' })
class TelaQueAbreODialogo {}

function abrirDialogo(celular: boolean) {
  const tela = new BehaviorSubject({ matches: celular, breakpoints: {} });
  TestBed.configureTestingModule({
    providers: [
      {
        provide: BreakpointObserver,
        useValue: {
          isMatched: () => tela.value.matches,
          observe: () => tela.asObservable(),
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(TelaQueAbreODialogo);
  fixture.detectChanges();

  TestBed.inject(AppDialogService).open(DialogoDeTeste, undefined, TITULO);
  fixture.detectChanges();
  // O foco inicial só entra quando a abertura termina, e quem termina é o
  // temporizador da animação: drenar a fila é o que torna o teste honesto.
  flush();
  fixture.detectChanges();

  const mudarTela = (paraCelular: boolean) => {
    tela.next({ matches: paraCelular, breakpoints: {} });
    fixture.detectChanges();
    flush();
  };

  return Object.assign(fixture, { mudarTela });
}

describe('AppDialogService', () => {
  // Parado no contêiner, o leitor de tela anuncia o rótulo do diálogo e nada
  // do que está escrito dentro dele. O título é o primeiro conteúdo real.
  it('entrega o foco ao título do diálogo, e não ao contêiner', fakeAsync(() => {
    const fixture = abrirDialogo(false);

    const focado = document.activeElement as HTMLElement;
    expect(focado.tagName).toBe('H2');
    expect(focado.textContent).toContain(TITULO);

    fixture.destroy();
    flush();
  }));

  // A largura vem do MatDialogConfig, e é isso que deixa um chamador ajustá-la
  // pelo parâmetro de configuração: repetida em folha de estilo com
  // `!important`, ela vencia o estilo embutido e o parâmetro virava enfeite.
  it('dimensiona o painel do celular pela configuração, não pela folha de estilo', fakeAsync(() => {
    const fixture = abrirDialogo(true);

    const painel = document.querySelector('.lactec-dialog-mobile') as HTMLElement;
    expect(painel).not.toBeNull();
    expect(painel.style.width).toBe('100vw');
    expect(painel.style.height).toBe('100dvh');

    fixture.destroy();
    flush();
  }));

  it('reajusta o painel quando a tela passa do celular para o computador e volta', fakeAsync(() => {
    const fixture = abrirDialogo(true);
    const painel = () => document.querySelector('.cdk-overlay-pane') as HTMLElement;

    fixture.mudarTela(false);

    expect(painel().classList).toContain('lactec-dialog-desktop');
    expect(painel().classList).not.toContain('lactec-dialog-mobile');
    expect(painel().style.width).toBe('min(700px, 92vw)');
    expect(painel().style.height).toBe('');

    fixture.mudarTela(true);

    expect(painel().classList).toContain('lactec-dialog-mobile');
    expect(painel().classList).not.toContain('lactec-dialog-desktop');
    expect(painel().style.width).toBe('100vw');
    expect(painel().style.height).toBe('100dvh');

    fixture.destroy();
    flush();
  }));
});
