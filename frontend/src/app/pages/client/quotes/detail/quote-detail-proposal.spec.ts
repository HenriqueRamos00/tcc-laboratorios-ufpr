import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import type { Quote } from '@/app/model/quote';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { LoginService } from '@/app/services/login.service';
import { QuotesService } from '@/app/services/quotes.service';

import { PdfViewerStubComponent, usePdfViewerStub } from './pdf-viewer.spec-util';
import { QuoteDetailComponent } from './quote-detail.component';

const DOWNLOAD_BUTTON = 'Baixar PDF';
const TEMPORARY_ADDRESS = 'blob:proposta-de-teste';
const EXPECTED_FILE_NAME = 'proposta-EAQ_2026_45484_V_1.pdf';

function quoteAtStage(stageName: string): Quote {
  return {
    id: 'quote-3',
    code: 'EAQ_2026_45484_V_1',
    name: 'Qualidade do ar: proposta de teste',
    status: 'Pendente de Aceite',
    stage: stageName,
    description: 'Coleta de 3 amostras e análise laboratorial',
    companyName: 'Empresa Exemplo Alfa Ltda',
    externalContactName: 'Equipe Comercial',
    externalContactEmail: 'comercial@example.com',
    totalPrice: 3450,
    createdDate: '2026-10-04',
  };
}

function proposalPdf(): Blob {
  return new Blob(['%PDF-1.4'], { type: 'application/pdf' });
}

function mountDetail(
  stageName: string,
  documentResponse: () => Observable<Blob> = () => of(proposalPdf()),
) {
  const notices: string[] = [];
  const askedFor: string[] = [];

  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: QuotesService,
        useValue: {
          getQuoteById: () => of(quoteAtStage(stageName)),
          quoteDocument: (quoteId: string) => {
            askedFor.push(quoteId);
            return documentResponse();
          },
        },
      },
      { provide: LoginService, useValue: { currentUser: () => 'Cliente Lactec' } },
      { provide: MatSnackBar, useValue: { open: (message: string) => notices.push(message) } },
      {
        provide: AppDialogService,
        useValue: { open: () => ({ afterClosed: () => of(undefined) }) },
      },
    ],
  });

  usePdfViewerStub();

  const fixture = TestBed.createComponent(QuoteDetailComponent);
  fixture.componentRef.setInput('id', 'quote-3');

  // Um ciclo por salto de toObservable, que entrega por effect: o id vira
  // orçamento, o orçamento monta o visualizador, e o visualizador pede o
  // documento.
  fixture.detectChanges();
  fixture.detectChanges();
  fixture.detectChanges();

  return { fixture, view: fixture.nativeElement as HTMLElement, notices, askedFor };
}

function buttonWith(view: HTMLElement, label: string): HTMLButtonElement | undefined {
  return Array.from(view.querySelectorAll('button')).find((button) =>
    button.textContent?.includes(label),
  );
}

describe('QuoteDetailComponent, a proposta dentro da página', () => {
  // Limite de baixo da regra: só existe proposta a partir da negociação, e um
  // visualizador vazio nas etapas anteriores afirmaria que o documento existe.
  it('sem proposta escrita a tela não monta o visualizador e diz o motivo', () => {
    const { view, askedFor } = mountDetail('Elaborando proposta');

    expect(view.querySelector('app-proposal-viewer')).toBeNull();
    expect(view.querySelector('ngx-extended-pdf-viewer')).toBeNull();
    expect(askedFor).toEqual([]);
    expect(view.textContent).toContain('Proposta em elaboração');
    expect(view.textContent).toContain('ainda está montando');
  });

  // E o limite de cima: em negociação o documento existe, e é com o Blob que o
  // visualizador é alimentado. ArrayBuffer aqui abriria a proposta uma vez só,
  // porque o pdf.js esvazia o buffer ao passá-lo para o worker.
  it('em negociação a proposta é exibida dentro da página, a partir do Blob', () => {
    const { fixture, view, askedFor } = mountDetail('Negociação');

    expect(view.querySelector('app-proposal-viewer')).not.toBeNull();
    expect(askedFor).toEqual(['quote-3']);

    const viewer = fixture.debugElement.query(By.directive(PdfViewerStubComponent))
      .componentInstance as PdfViewerStubComponent;

    expect(viewer.src() instanceof Blob).toBeTrue();
    expect(viewer.filenameForDownload()).toBe(EXPECTED_FILE_NAME);
  });

  it('o botão Baixar PDF pede o documento ao serviço e entrega o arquivo ao navegador', fakeAsync(() => {
    const create = spyOn(URL, 'createObjectURL').and.returnValue(TEMPORARY_ADDRESS);
    const revoke = spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    const { fixture, view, askedFor } = mountDetail('Negociação');

    buttonWith(view, DOWNLOAD_BUTTON)?.click();
    fixture.detectChanges();

    expect(askedFor).toEqual(['quote-3', 'quote-3']);
    expect(create.calls.mostRecent().args[0] instanceof Blob).toBeTrue();
    expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe(
      EXPECTED_FILE_NAME,
    );

    tick();
    expect(revoke).toHaveBeenCalledWith(TEMPORARY_ADDRESS);
  }));

  // Proposta sem PDF publicado tem que falhar avisando, e não salvar um arquivo
  // que não é o orçamento do cliente.
  it('quando o documento falha, o cliente é avisado e nada é salvo', () => {
    const create = spyOn(URL, 'createObjectURL');
    const { fixture, view, notices } = mountDetail('Negociação', () =>
      throwError(() => new Error('sem documento')),
    );

    buttonWith(view, DOWNLOAD_BUTTON)?.click();
    fixture.detectChanges();

    expect(notices).toEqual(['Não foi possível baixar o PDF da proposta no momento']);
    expect(create).not.toHaveBeenCalled();
    expect(view.textContent).toContain('Não foi possível abrir a proposta agora');
  });
});
