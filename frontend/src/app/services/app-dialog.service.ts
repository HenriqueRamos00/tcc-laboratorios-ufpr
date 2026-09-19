import { BreakpointObserver } from '@angular/cdk/layout';
import { inject, Injectable, Type } from '@angular/core';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';

const HANDSET_QUERY = '(max-width: 767.98px)';

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

    // No mobile o overlay ocupa a tela inteira e fica transparente: a própria
    // estrutura do modal (barra que cobre a navigation real + escurecedor + sheet)
    // é desenhada dentro do dialog-shell. Por isso o backdrop do CDK é desligado.
    const mobile: MatDialogConfig<D> = {
      width: '100vw',
      maxWidth: '100vw',
      height: '100dvh',
      position: { top: '0', left: '0' },
      hasBackdrop: false,
      panelClass: 'lactec-dialog-mobile',
      // Sem a animação de zoom do Material: a barra falsa e o escurecedor
      // aparecem na hora. Quem anima é só o sheet (CSS, deslizando de baixo).
      enterAnimationDuration: '0ms',
      exitAnimationDuration: '0ms',
    };

    const desktop: MatDialogConfig<D> = {
      width: '560px',
      maxWidth: '92vw',
      panelClass: 'lactec-dialog-desktop',
    };

    return this.dialog.open<T, D, R>(component, {
      data,
      ariaLabel,
      autoFocus: 'dialog',
      ...(handset ? mobile : desktop),
      ...extra,
    });
  }
}
