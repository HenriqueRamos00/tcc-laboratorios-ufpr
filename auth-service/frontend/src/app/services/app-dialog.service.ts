import { inject, Injectable, Type } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

@Injectable({ providedIn: 'root' })
export class AppDialogService {
  private readonly dialog = inject(MatDialog);

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
    return this.open<T, D, R>(component, data, ariaLabel, {
      width: '520px',
      minWidth: '0',
      maxWidth: '100vw',
      height: '100dvh',
      maxHeight: '100dvh',
      position: { top: '0', right: '0' },
      hasBackdrop: true,
      panelClass: 'lactec-dialog-lateral',
    });
  }
}
