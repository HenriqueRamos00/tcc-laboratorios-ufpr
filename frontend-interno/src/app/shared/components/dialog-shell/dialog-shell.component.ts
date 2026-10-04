import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-dialog-shell',
  standalone: true,
  imports: [MatButtonModule, MatIconModule],
  templateUrl: './dialog-shell.component.html',
  styleUrl: './dialog-shell.component.css',
  host: { '[class.dialog-shell--lateral]': 'lateral()' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogShellComponent {
  private readonly dialogRef = inject(MatDialogRef, { optional: true });

  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly lateral = input(false);
  readonly closeDisabled = input(false);
  readonly closeLabel = input('Fechar diálogo');

  close(): void {
    if (!this.closeDisabled() && !this.dialogRef?.disableClose) this.dialogRef?.close();
  }
}
