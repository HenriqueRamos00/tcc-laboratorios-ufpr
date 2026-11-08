import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'app-action-button',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, NgTemplateOutlet],
  template: `
    <ng-template #content>
      <span class="action-button__content">
        @if (loading()) {
          <mat-spinner class="action-button__spinner" diameter="18" aria-hidden="true" />
        } @else if (icon()) {
          <mat-icon class="action-button__icon" aria-hidden="true">{{ icon() }}</mat-icon>
        }
        <span class="action-button__label">{{ label() }}</span>
      </span>
    </ng-template>

    @if (variant() === 'primary') {
      <button
        mat-flat-button
        color="primary"
        class="action-button"
        [type]="type()"
        [disabled]="disabled() || loading()"
        (click)="activated.emit()"
      >
        <ng-container *ngTemplateOutlet="content" />
      </button>
    } @else {
      <button
        mat-stroked-button
        class="action-button"
        [type]="type()"
        [disabled]="disabled() || loading()"
        (click)="activated.emit()"
      >
        <ng-container *ngTemplateOutlet="content" />
      </button>
    }
  `,
  styleUrl: './action-button.component.css',
  host: {
    '[class.action-button--full-width-on-mobile]': 'fullWidthOnMobile()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActionButtonComponent {
  readonly label = input.required<string>();
  readonly icon = input('');
  readonly variant = input<'primary' | 'secondary'>('primary');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly fullWidthOnMobile = input(false);

  readonly activated = output<void>();
}
