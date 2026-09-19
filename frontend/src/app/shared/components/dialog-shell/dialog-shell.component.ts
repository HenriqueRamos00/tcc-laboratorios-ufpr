import { BreakpointObserver } from '@angular/cdk/layout';
import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { map } from 'rxjs';

const HANDSET_QUERY = '(max-width: 767.98px)';

@Component({
  selector: 'app-dialog-shell',
  standalone: true,
  imports: [NgTemplateOutlet, MatButtonModule, MatIconModule],
  templateUrl: './dialog-shell.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogShellComponent {
  private readonly dialogRef = inject(MatDialogRef, { optional: true });
  private readonly breakpoints = inject(BreakpointObserver);

  readonly title = input<string>();
  readonly subtitle = input<string>();

  /** No mobile o fechar vive na navigation bar do topo, não dentro do modal. */
  readonly isHandset = toSignal(
    this.breakpoints.observe([HANDSET_QUERY]).pipe(map((estado) => estado.matches)),
    { initialValue: false },
  );

  close(): void {
    this.dialogRef?.close();
  }
}
