import { Component, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { By } from '@angular/platform-browser';
import { Observable, Subject, of, throwError } from 'rxjs';

import { PdfViewerComponent } from './pdf-viewer.component';
import { PdfViewerDialog } from './pdf-viewer-dialog.service';
import { PdfViewerStubComponent, usePdfViewerStub } from './pdf-viewer.spec-util';

function pdf(): Blob {
  return new Blob(['%PDF-1.4'], { type: 'application/pdf' });
}

function overlay(): HTMLElement {
  return document.querySelector('.cdk-overlay-container') as HTMLElement;
}

function dialogPanel(): HTMLElement | null {
  return document.querySelector('app-pdf-viewer-dialog');
}

async function settle(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

@Component({
  standalone: true,
  template: `<button type="button" id="abrir" (click)="open()">Visualizar</button>`,
})
class OpenerComponent {
  private readonly viewer = inject(PdfViewerDialog);
  source: Observable<Blob> = of(pdf());

  open(): void {
    this.viewer.open({ source: this.source, title: 'Relatório LACTEC-1', fileName: 'relatorio.pdf' });
  }
}

@Component({
  standalone: true,
  imports: [PdfViewerComponent],
  template: `<app-pdf-viewer title="Proposta comercial" fileName="proposta.pdf" [source]="source()" />`,
})
class InlineHostComponent {
  readonly source = signal<Observable<Blob>>(of(pdf()));
}

describe('Visualizador de PDF em tela cheia', () => {
  beforeEach(() => {
    usePdfViewerStub();
  });

  afterEach(async () => {
    TestBed.inject(MatDialog).closeAll();
    await settle();
    TestBed.resetTestingModule();
  });

  it('abre o documento direto em tela cheia, com o Blob, e um botão de fechar grande e nomeado', async () => {
    const fixture = TestBed.createComponent(OpenerComponent);
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('#abrir') as HTMLButtonElement).click();
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    const fechar = overlay().querySelector('button[aria-label="Fechar visualizador"]') as HTMLButtonElement;
    expect(dialogPanel()).not.toBeNull();
    expect(overlay().querySelector('[role="dialog"]')?.getAttribute('aria-labelledby')).toBe('pdf-viewer-dialog-title');
    expect(overlay().querySelector('#pdf-viewer-dialog-title')?.textContent).toContain('Relatório LACTEC-1');
    expect(fechar.className).toContain('h-12');
    expect(overlay().querySelector('ngx-extended-pdf-viewer')).not.toBeNull();
    expect(overlay().querySelector('.lactec-pdf-viewer-panel')).not.toBeNull();
  });

  it('ESC fecha o diálogo e o foco volta ao botão que o abriu', async () => {
    const fixture = TestBed.createComponent(OpenerComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
    const abrir = fixture.nativeElement.querySelector('#abrir') as HTMLButtonElement;

    abrir.focus();
    abrir.click();
    fixture.detectChanges();
    await fixture.whenStable();
    await settle();

    const painel = overlay().querySelector('.mat-mdc-dialog-container') as HTMLElement;
    expect(painel.contains(document.activeElement)).toBeTrue();

    painel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    fixture.detectChanges();
    await fixture.whenStable();
    await settle();

    expect(dialogPanel()).toBeNull();
    expect(document.activeElement).toBe(abrir);
    fixture.nativeElement.remove();
  });

  it('mostra carregando enquanto o PDF não chega e erro com nova tentativa quando falha', async () => {
    const pendente = new Subject<Blob>();
    const fixture = TestBed.createComponent(OpenerComponent);
    fixture.componentInstance.source = pendente.asObservable();
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('#abrir') as HTMLButtonElement).click();
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();
    expect(overlay().querySelector('app-loading-region')?.getAttribute('aria-busy')).toBe('true');

    pendente.error(new Error('falhou'));
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();
    expect(overlay().textContent).toContain('Não foi possível abrir o documento agora');
  });

  it('a nova tentativa assina a fonte de novo', async () => {
    let assinaturas = 0;
    const fixture = TestBed.createComponent(OpenerComponent);
    fixture.componentInstance.source = new Observable<Blob>((subscriber) => {
      assinaturas++;
      if (assinaturas === 1) subscriber.error(new Error('falhou'));
      else subscriber.next(pdf());
    });
    fixture.detectChanges();

    (fixture.nativeElement.querySelector('#abrir') as HTMLButtonElement).click();
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    const tentar = [...overlay().querySelectorAll('button')].find((botao) =>
      botao.textContent?.includes('Tentar de novo'),
    ) as HTMLButtonElement;
    tentar.click();
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    expect(assinaturas).toBe(2);
    expect(overlay().querySelector('ngx-extended-pdf-viewer')).not.toBeNull();
  });

  it('no visualizador embutido, Ver em tela cheia abre o mesmo Blob no diálogo sem buscar de novo', async () => {
    let buscas = 0;
    const fixture = TestBed.createComponent(InlineHostComponent);
    fixture.componentInstance.source.set(
      new Observable<Blob>((subscriber) => {
        buscas++;
        subscriber.next(pdf());
      }),
    );
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    const embutido = fixture.debugElement.query(By.directive(PdfViewerStubComponent));
    expect(embutido).not.toBeNull();
    const blob = (embutido.componentInstance as PdfViewerStubComponent).src();

    const telaCheia = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].find((botao) =>
      botao.textContent?.includes('Ver em tela cheia'),
    ) as HTMLButtonElement;
    telaCheia.click();
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    expect(buscas).toBe(1);
    expect(fixture.debugElement.query(By.directive(PdfViewerStubComponent))).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('aberto em tela cheia');
    expect(dialogPanel()).not.toBeNull();
    expect(blob instanceof Blob).toBeTrue();
  });

  it('mostra o erro do visualizador embutido quando o documento falha', async () => {
    const fixture = TestBed.createComponent(InlineHostComponent);
    fixture.componentInstance.source.set(throwError(() => new Error('sem documento')));
    fixture.detectChanges();
    await settle();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Não foi possível abrir o documento agora');
  });
});
