import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { of, type Observable } from 'rxjs';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { LoadingRegionComponent } from '@shared/components/skeleton/loading-region.component';

import { loadPdf, PdfDocumentComponent } from './pdf-document.component';
import { PdfSkeletonComponent } from './pdf-skeleton.component';
import { PdfViewerDialog } from './pdf-viewer-dialog.service';

const INLINE_HEIGHT = '70vh';

@Component({
  selector: 'app-pdf-viewer',
  standalone: true,
  imports: [
    MatIconModule,
    PdfDocumentComponent,
    EmptyStateComponent,
    LoadingRegionComponent,
    PdfSkeletonComponent,
  ],
  host: { class: 'block' },
  template: `
    <div class="overflow-hidden rounded-xl border border-lactec-line bg-lactec-paper">
      <div
        class="flex flex-wrap items-center justify-between gap-2 border-b border-lactec-line
               px-3 py-2"
      >
        <p class="m-0 text-sm font-semibold text-lactec-ink">{{ title() }}</p>
        <button
          type="button"
          class="inline-flex min-h-11 items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold
                 text-lactec-ink-soft hover:bg-lactec-line-soft disabled:opacity-40"
          [disabled]="!pdf.file()"
          (click)="openFullScreen()"
        >
          <mat-icon class="icone-20" aria-hidden="true">open_in_full</mat-icon>
          Ver em tela cheia
        </button>
      </div>

      @if (pdf.file(); as file) {
        @if (fullScreen()) {
          <p
            class="m-0 flex items-center justify-center px-4 text-center text-sm text-lactec-muted"
            [style.height]="height"
          >
            O documento está aberto em tela cheia.
          </p>
        } @else {
          <app-pdf-document [file]="file" [fileName]="fileName()" [height]="height" />
        }
      } @else if (pdf.failed()) {
        <app-empty-state
          tom="erro"
          icon="cloud_off"
          [title]="errorTitle()"
          [descricao]="errorDescription()"
        >
          <button
            type="button"
            class="mt-2 inline-flex h-11 items-center gap-2 rounded-lg border border-lactec-line
                   px-4 text-sm font-semibold text-lactec-ink hover:bg-lactec-surface"
            (click)="pdf.retry()"
          >
            <mat-icon class="icone-20" aria-hidden="true">refresh</mat-icon>
            Tentar de novo
          </button>
        </app-empty-state>
      } @else {
        <app-loading-region [label]="loadingLabel()">
          <app-pdf-skeleton [framed]="false" [height]="height" />
        </app-loading-region>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerComponent {
  readonly source = input.required<Observable<Blob>>();
  readonly title = input.required<string>();
  readonly fileName = input.required<string>();
  readonly loadingLabel = input('Carregando o documento…');
  readonly errorTitle = input('Não foi possível abrir o documento agora');
  readonly errorDescription = input(
    'O documento não respondeu. Tente de novo em instantes ou baixe o PDF para ler fora do portal.',
  );

  private readonly viewerDialog = inject(PdfViewerDialog);

  protected readonly height = INLINE_HEIGHT;
  protected readonly pdf = loadPdf(this.source);
  protected readonly fullScreen = signal(false);

  protected openFullScreen(): void {
    const file = this.pdf.file();
    if (!file || this.fullScreen()) return;
    this.fullScreen.set(true);
    this.viewerDialog
      .open({ source: of(file), title: this.title(), fileName: this.fileName(), errorTitle: this.errorTitle() })
      .afterClosed()
      .subscribe(() => this.fullScreen.set(false));
  }
}
