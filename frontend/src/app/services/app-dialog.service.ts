import { BreakpointObserver } from '@angular/cdk/layout';
import { inject, Injectable, Type } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';
import { distinctUntilChanged, map, skip, takeUntil } from 'rxjs';

const HANDSET_QUERY = '(max-width: 767.98px)';
const MOBILE_PANEL = 'lactec-dialog-mobile';
const DESKTOP_PANEL = 'lactec-dialog-desktop';

interface DialogLayout {
  readonly width: string;
  readonly height: string;
  readonly position: { top?: string; left?: string };
  readonly panelClass: string;
}

function layoutFor(handset: boolean): DialogLayout {
  return handset
    ? { width: '100vw', height: '100dvh', position: { top: '0', left: '0' }, panelClass: MOBILE_PANEL }
    : { width: 'min(700px, 92vw)', height: '', position: {}, panelClass: DESKTOP_PANEL };
}

@Injectable({ providedIn: 'root' })
export class AppDialogService {
  private readonly dialog = inject(MatDialog);
  private readonly breakpoints = inject(BreakpointObserver);

  /**
   * @param ariaLabel Nome acessível do diálogo. A doc do MatDialog pede
   *   `ariaLabel` ou `ariaLabelledBy` no MatDialogConfig: sem um dos dois o
   *   leitor de tela anuncia apenas "diálogo".
   */
  open<T, D = unknown, R = unknown>(
    component: Type<T>,
    data: D | undefined,
    ariaLabel: string,
    extra?: MatDialogConfig<D>,
  ): MatDialogRef<T, R> {
    const handset = this.breakpoints.isMatched(HANDSET_QUERY);

    const ref = this.dialog.open<T, D, R>(component, {
      data,
      ariaLabel,
      autoFocus: 'first-heading',
      maxWidth: '100vw',
      ...layoutFor(handset),
      ...(handset ? { enterAnimationDuration: '0ms', exitAnimationDuration: '0ms' } : {}),
      ...extra,
    });

    this.breakpoints
      .observe(HANDSET_QUERY)
      .pipe(
        map((state) => state.matches),
        distinctUntilChanged(),
        skip(1),
        takeUntil(ref.afterClosed()),
      )
      .subscribe((nowHandset) => {
        const layout = layoutFor(nowHandset);
        ref.removePanelClass(nowHandset ? DESKTOP_PANEL : MOBILE_PANEL);
        ref.addPanelClass(layout.panelClass);
        ref.updateSize(layout.width, layout.height);
        ref.updatePosition(layout.position);
      });

    return ref;
  }
}
