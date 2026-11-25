import { BreakpointObserver } from '@angular/cdk/layout';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  signal,
  type Signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import {
  NgxExtendedPdfViewerModule,
  ScrollModeType,
  pdfDefaultOptions,
} from 'ngx-extended-pdf-viewer';
import { catchError, EMPTY, map, switchMap, tap, type Observable } from 'rxjs';

pdfDefaultOptions.enableScripting = false;
pdfDefaultOptions.assetsFolder = 'assets';

const NARROW_SCREEN = '(max-width: 767.98px)';

export interface PdfLoad {
  readonly file: Signal<Blob | null>;
  readonly failed: Signal<boolean>;
  retry(): void;
}

export function loadPdf(source: Signal<Observable<Blob>>): PdfLoad {
  const file = signal<Blob | null>(null);
  const failed = signal(false);
  const attempt = signal(0);

  toObservable(computed(() => ({ source: source(), attempt: attempt() })))
    .pipe(
      tap(() => {
        file.set(null);
        failed.set(false);
      }),
      switchMap(({ source: document }) =>
        document.pipe(
          catchError(() => {
            failed.set(true);
            return EMPTY;
          }),
        ),
      ),
      takeUntilDestroyed(),
    )
    .subscribe((loaded) => file.set(loaded));

  return {
    file: file.asReadonly(),
    failed: failed.asReadonly(),
    retry: () => attempt.update((count) => count + 1),
  };
}

@Component({
  selector: 'app-pdf-document',
  standalone: true,
  imports: [NgxExtendedPdfViewerModule],
  host: { class: 'block' },
  template: `
    <ngx-extended-pdf-viewer
      [src]="file()"
      [height]="height()"
      [zoom]="zoom()"
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
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfDocumentComponent {
  readonly file = input.required<Blob>();
  readonly fileName = input.required<string>();
  readonly height = input('70vh');

  private readonly breakpoints = inject(BreakpointObserver);

  protected readonly ScrollModeType = ScrollModeType;

  protected readonly zoom = toSignal(
    this.breakpoints
      .observe(NARROW_SCREEN)
      .pipe(map((state) => (state.matches ? 'page-width' : '100%'))),
    { initialValue: this.breakpoints.isMatched(NARROW_SCREEN) ? 'page-width' : '100%' },
  );
}
