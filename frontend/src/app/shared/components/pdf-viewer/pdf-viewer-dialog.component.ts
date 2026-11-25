import { A11yModule } from '@angular/cdk/a11y';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import type { Observable } from 'rxjs';

import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { LoadingRegionComponent } from '@shared/components/skeleton/loading-region.component';

import { loadPdf, PdfDocumentComponent } from './pdf-document.component';
import { PdfSkeletonComponent } from './pdf-skeleton.component';

export interface PdfViewerDialogData {
  readonly source: Observable<Blob>;
  readonly title: string;
  readonly fileName: string;
  readonly errorTitle?: string;
}

export const PDF_VIEWER_DIALOG_TITLE_ID = 'pdf-viewer-dialog-title';

const DEFAULT_ERROR_TITLE = 'Não foi possível abrir o documento agora';

@Component({
  selector: 'app-pdf-viewer-dialog',
  standalone: true,
  imports: [
    A11yModule,
    MatIconModule,
    PdfDocumentComponent,
    EmptyStateComponent,
    LoadingRegionComponent,
    PdfSkeletonComponent,
  ],
  host: {
    class:
      'flex h-full w-full max-w-full flex-col overflow-hidden bg-lactec-paper ' +
      'pt-[env(safe-area-inset-top)] pr-[env(safe-area-inset-right)] ' +
      'pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)]',
  },
  template: `
    <header class="flex shrink-0 items-center gap-3 border-b border-lactec-line px-3 py-2">
      <h2
        [id]="titleId"
        class="m-0 min-w-0 flex-1 truncate text-base font-semibold text-lactec-ink"
      >
        {{ data.title }}
      </h2>
      <button
        type="button"
        cdkFocusInitial
        class="inline-flex h-12 min-w-12 shrink-0 items-center justify-center gap-2 rounded-lg
               bg-lactec-primary-deep px-4 text-sm font-semibold text-lactec-on-primary
               hover:bg-lactec-primary-deep-strong focus-visible:outline-2
               focus-visible:outline-offset-2"
        aria-label="Fechar visualizador"
        (click)="close()"
      >
        <mat-icon aria-hidden="true">close</mat-icon>
        <span class="hidden sm:inline" aria-hidden="true">Fechar</span>
      </button>
    </header>

    <div class="relative min-h-0 flex-1 overflow-hidden">
      @if (pdf.file(); as file) {
        <app-pdf-document
          class="absolute inset-0"
          [file]="file"
          [fileName]="data.fileName"
          height="100%"
        />
      } @else if (pdf.failed()) {
        <div class="flex h-full items-center justify-center p-4">
          <app-empty-state
            tom="erro"
            icon="cloud_off"
            [title]="data.errorTitle ?? defaultErrorTitle"
            descricao="O documento não respondeu. Tente de novo em instantes ou feche e baixe o PDF."
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
        </div>
      } @else {
        <app-loading-region class="absolute inset-0" label="Carregando o documento…">
          <app-pdf-skeleton [framed]="false" height="100%" />
        </app-loading-region>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerDialogComponent {
  protected readonly data = inject<PdfViewerDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<PdfViewerDialogComponent>);

  protected readonly titleId = PDF_VIEWER_DIALOG_TITLE_ID;
  protected readonly defaultErrorTitle = DEFAULT_ERROR_TITLE;
  protected readonly pdf = loadPdf(signal(this.data.source));

  protected close(): void {
    this.dialogRef.close();
  }
}
