import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgxExtendedPdfViewerModule, type PdfSrcType } from 'ngx-extended-pdf-viewer';

import { ProposalViewerComponent } from './proposal-viewer.component';

/**
 * Dublê do visualizador de PDF. O de verdade busca o pdf.js em /assets durante
 * a execução, e a suíte não espera esse carregamento; o que os testes precisam
 * saber é se a tela montou o visualizador e com qual documento.
 *
 * Os inputs repetem um a um os que a tela liga, de propósito: com
 * `NO_ERRORS_SCHEMA` um binding escrito errado passaria calado, e aqui o
 * compilador continua conferindo cada um. Quem confere os bindings contra a
 * biblioteca de verdade é o `ng build`, que compila o template real em AOT.
 */
@Component({
  selector: 'ngx-extended-pdf-viewer',
  standalone: true,
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfViewerStubComponent {
  readonly src = input<PdfSrcType>();
  readonly height = input<string>();
  readonly zoom = input<string | number>();
  readonly language = input<string>();
  readonly filenameForDownload = input<string>();
  readonly pageViewMode = input<string>();
  readonly scrollMode = input<number>();
  readonly textLayer = input<boolean>();
  readonly showOpenFileButton = input<boolean>();
  readonly showPresentationModeButton = input<boolean>();
  readonly showEditorButtons = input<boolean>();
  readonly enableDragAndDrop = input<boolean>();
}

/**
 * O visualizador é importado pelo componente standalone, então trocá-lo pelo
 * dublê só é possível reescrevendo os imports do próprio componente.
 */
export function usePdfViewerStub(): void {
  TestBed.overrideComponent(ProposalViewerComponent, {
    remove: { imports: [NgxExtendedPdfViewerModule] },
    add: { imports: [PdfViewerStubComponent] },
  });
}
