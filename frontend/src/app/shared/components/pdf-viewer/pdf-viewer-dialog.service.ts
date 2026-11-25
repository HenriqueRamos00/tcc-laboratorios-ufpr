import { inject, Injectable } from '@angular/core';
import { MatDialog, type MatDialogRef } from '@angular/material/dialog';

import {
  PDF_VIEWER_DIALOG_TITLE_ID,
  PdfViewerDialogComponent,
  type PdfViewerDialogData,
} from './pdf-viewer-dialog.component';

export const PDF_VIEWER_PANEL_CLASS = 'lactec-pdf-viewer-panel';

@Injectable({ providedIn: 'root' })
export class PdfViewerDialog {
  private readonly dialog = inject(MatDialog);

  open(data: PdfViewerDialogData): MatDialogRef<PdfViewerDialogComponent> {
    return this.dialog.open(PdfViewerDialogComponent, {
      data,
      ariaLabelledBy: PDF_VIEWER_DIALOG_TITLE_ID,
      autoFocus: 'first-tabbable',
      restoreFocus: true,
      closeOnNavigation: true,
      width: '100vw',
      maxWidth: '100vw',
      height: '100dvh',
      maxHeight: '100dvh',
      position: { top: '0', left: '0' },
      panelClass: PDF_VIEWER_PANEL_CLASS,
    });
  }
}
