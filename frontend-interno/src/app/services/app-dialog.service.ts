import { DOCUMENT } from '@angular/common';
import { inject, Injectable, Type } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

@Injectable({ providedIn: 'root' })
export class AppDialogService {
  private readonly dialog = inject(MatDialog);
  private readonly document = inject(DOCUMENT);
  private lateralDialogsOpen = 0;

  open<T, D = unknown, R = unknown>(
    component: Type<T>,
    data: D | undefined,
    ariaLabel: string,
    extra?: MatDialogConfig<D>,
  ): MatDialogRef<T, R> {
    return this.dialog.open<T, D, R>(component, {
      data,
      ariaLabel,
      autoFocus: 'dialog',
      width: '560px',
      maxWidth: '92vw',
      panelClass: 'lactec-dialog-desktop',
      ...extra,
    });
  }

  openLateral<T, D = unknown, R = unknown>(
    component: Type<T>,
    data: D | undefined,
    ariaLabel: string,
  ): MatDialogRef<T, R> {
    const ref = this.open<T, D, R>(component, data, ariaLabel, {
      width: '520px',
      minWidth: '0',
      maxWidth: '100vw',
      height: '100dvh',
      maxHeight: '100dvh',
      position: { top: '0', right: '0' },
      hasBackdrop: true,
      panelClass: 'lactec-dialog-lateral',
    });

    this.lateralDialogsOpen++;
    this.document.body.classList.add('lactec-lateral-dialog-open');
    ref.afterClosed().subscribe(() => {
      this.lateralDialogsOpen--;
      if (this.lateralDialogsOpen === 0) {
        this.document.body.classList.remove('lactec-lateral-dialog-open');
      }
    });

    return ref;
  }
}
