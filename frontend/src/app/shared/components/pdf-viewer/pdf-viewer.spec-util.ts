import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { NgxExtendedPdfViewerModule, type PdfSrcType } from 'ngx-extended-pdf-viewer';

import { PdfDocumentComponent } from './pdf-document.component';

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

export function usePdfViewerStub(): void {
  TestBed.overrideComponent(PdfDocumentComponent, {
    remove: { imports: [NgxExtendedPdfViewerModule] },
    add: { imports: [PdfViewerStubComponent] },
  });
}
