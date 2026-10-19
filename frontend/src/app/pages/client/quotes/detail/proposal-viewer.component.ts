import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import {
  NgxExtendedPdfViewerModule,
  ScrollModeType,
  pdfDefaultOptions,
} from 'ngx-extended-pdf-viewer';
import { catchError, EMPTY, switchMap, tap } from 'rxjs';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';

import { QuotesService } from '@/app/services/quotes.service';

// Um PDF pode trazer JavaScript dentro do arquivo, e proposta comercial não
// precisa executar nada. A biblioteca já entrega desligado; a linha fica
// escrita porque isso é decisão de segurança e não default herdado.
pdfDefaultOptions.enableScripting = false;

// O pdf.js procura o worker, os cmaps, as fontes padrão e a tradução dentro
// desta pasta, e é para ela que o `angular.json` copia os arquivos da
// biblioteca. Mexer num dos dois lados sem o outro deixa a tela em branco.
pdfDefaultOptions.assetsFolder = 'assets';

/**
 * A proposta aparece dentro da página de detalhe, com rolagem contínua entre as
 * páginas, e cresce para a janela inteira quando o cliente pede.
 *
 * O arquivo chega como `Blob` pelo `HttpClient` porque ele fica atrás do token
 * do portal: quem busca é o cliente HTTP, não a barra de endereços. É o `Blob`
 * que fica guardado, nunca um `ArrayBuffer`, porque o pdf.js transfere o buffer
 * para o worker e o deixa vazio, e a segunda abertura do mesmo documento viria
 * em branco.
 *
 * Os componentes da biblioteca não são standalone, por isso o que entra em
 * `imports` é o NgModule dela.
 *
 * Quem decide se existe proposta é a página de detalhe, pela etapa do
 * orçamento: este componente só é montado quando há documento a buscar.
 */
@Component({
  selector: 'app-proposal-viewer',
  standalone: true,
  imports: [NgxExtendedPdfViewerModule, MatIconModule, EmptyStateComponent],
  host: {
    class: 'block',
    '(document:keydown.escape)': 'leaveFullScreen()',
    '(document:fullscreenchange)': 'syncFullScreen()',
  },
  template: `
    <div
      #panel
      class="overflow-hidden rounded-xl border border-lactec-line bg-lactec-paper"
      [class.fixed]="fullScreen()"
      [class.inset-0]="fullScreen()"
      [class.z-50]="fullScreen()"
      [class.rounded-none]="fullScreen()"
      [attr.data-fullscreen]="fullScreen() ? '' : null"
    >
      <div
        class="flex flex-wrap items-center justify-between gap-2 border-b border-lactec-line
               px-3 py-2"
      >
        <p class="m-0 text-sm font-semibold text-lactec-ink">Proposta comercial</p>
        <button
          type="button"
          class="inline-flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold
                 text-lactec-ink-soft hover:bg-lactec-line-soft"
          [attr.aria-pressed]="fullScreen()"
          (click)="toggleFullScreen()"
        >
          <mat-icon class="icone-20" aria-hidden="true">
            {{ fullScreen() ? 'close_fullscreen' : 'open_in_full' }}
          </mat-icon>
          {{ fullScreen() ? 'Sair da tela cheia' : 'Ver em tela cheia' }}
        </button>
      </div>

      @if (proposalFile(); as file) {
        <ngx-extended-pdf-viewer
          [src]="file"
          [height]="viewerHeight()"
          [zoom]="'100%'"
          [language]="'pt-BR'"
          [filenameForDownload]="fileName()"
          [pageViewMode]="'multiple'"
          [scrollMode]="ScrollModeType.vertical"
          [textLayer]="true"
          [showOpenFileButton]="false"
          [showPresentationModeButton]="false"
          [showEditorButtons]="false"
          [enableDragAndDrop]="false"
        />
      } @else if (loadFailed()) {
        <app-empty-state
          tom="erro"
          icon="cloud_off"
          title="Não foi possível abrir a proposta agora"
          descricao="O documento não respondeu. Tente de novo em instantes ou baixe o PDF para ler fora do portal."
        />
      } @else {
        <p class="m-0 px-3 py-12 text-center text-sm text-lactec-muted" role="status">
          Carregando a proposta…
        </p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProposalViewerComponent {
  /** Identificador do orçamento no Salesforce, o que a API entende. */
  readonly quoteId = input.required<string>();

  /** Nome do arquivo quando o cliente baixa pela barra do visualizador. */
  readonly fileName = input.required<string>();

  private readonly quotes = inject(QuotesService);
  private readonly panel = viewChild.required<ElementRef<HTMLElement>>('panel');

  protected readonly ScrollModeType = ScrollModeType;
  protected readonly proposalFile = signal<Blob | null>(null);
  protected readonly loadFailed = signal(false);
  protected readonly fullScreen = signal(false);

  // Altura em unidade de viewport, e não em porcentagem: a biblioteca repassa
  // este valor para o style do próprio contêiner, e porcentagem dentro de um
  // cartão sem altura declarada resolve para zero.
  protected readonly viewerHeight = computed(() =>
    this.fullScreen() ? 'calc(100vh - 4rem)' : '70vh',
  );

  // O input chega por binding do template, então ele ainda não tem valor no
  // construtor: quem espera por ele é o toObservable, como a página de detalhe
  // já faz com o id da rota. A troca de orçamento também cancela a busca
  // anterior pelo switchMap, o que um effect teria que fazer à mão.
  constructor() {
    toObservable(this.quoteId)
      .pipe(
        tap(() => {
          this.proposalFile.set(null);
          this.loadFailed.set(false);
        }),
        switchMap((quoteId) =>
          this.quotes.quoteDocument(quoteId).pipe(
            catchError(() => {
              this.loadFailed.set(true);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((file) => this.proposalFile.set(file));
  }

  // A tela cheia é a do navegador, e não o modo apresentação do pdf.js: o modo
  // apresentação mostra uma página por vez e abandona a rolagem contínua, que é
  // como o cliente lê a proposta. Quando o navegador recusa o pedido, a classe
  // do painel sozinha já cobre a área do conteúdo, então o botão não fica
  // inerte.
  protected toggleFullScreen(): void {
    if (this.fullScreen()) {
      this.leaveFullScreen();
      return;
    }
    this.fullScreen.set(true);
    void this.panel().nativeElement.requestFullscreen?.().catch(() => undefined);
  }

  protected leaveFullScreen(): void {
    this.fullScreen.set(false);
    if (document.fullscreenElement) void document.exitFullscreen();
  }

  protected syncFullScreen(): void {
    if (!document.fullscreenElement) this.fullScreen.set(false);
  }
}
