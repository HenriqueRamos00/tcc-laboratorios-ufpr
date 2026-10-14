import { BreakpointObserver } from '@angular/cdk/layout';
import { Component } from '@angular/core';
import { fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';

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
  TestBed.configureTestingModule({
    providers: [
      {
        provide: BreakpointObserver,
        useValue: {
          isMatched: () => celular,
          observe: () => of({ matches: celular, breakpoints: {} }),
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

  return fixture;
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
});
