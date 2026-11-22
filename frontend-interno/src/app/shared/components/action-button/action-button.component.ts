import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink, type Params } from '@angular/router';

@Component({
  selector: 'app-action-button',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, NgTemplateOutlet, RouterLink],
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
      @if (hasLink()) {
        <a
          mat-flat-button
          color="primary"
          class="action-button"
          [routerLink]="linkDestination()"
          [queryParams]="queryParams()"
          [disabled]="unavailable()"
          [attr.aria-disabled]="unavailable() ? 'true' : null"
          [attr.tabindex]="unavailable() ? -1 : null"
          [attr.aria-label]="ariaLabel() || null"
          [class.action-button--unavailable]="unavailable()"
          (click)="onClick()"
        >
          <ng-container *ngTemplateOutlet="content" />
        </a>
      } @else {
        <button
          mat-flat-button
          color="primary"
          class="action-button"
          [type]="type()"
          [disabled]="unavailable()"
          (click)="onClick()"
        >
          <ng-container *ngTemplateOutlet="content" />
        </button>
      }
    } @else if (variant() === 'secondary') {
      @if (hasLink()) {
        <a
          mat-stroked-button
          class="action-button"
          [routerLink]="linkDestination()"
          [queryParams]="queryParams()"
          [disabled]="unavailable()"
          [attr.aria-disabled]="unavailable() ? 'true' : null"
          [attr.tabindex]="unavailable() ? -1 : null"
          [attr.aria-label]="ariaLabel() || null"
          [class.action-button--unavailable]="unavailable()"
          (click)="onClick()"
        >
          <ng-container *ngTemplateOutlet="content" />
        </a>
      } @else {
        <button
          mat-stroked-button
          class="action-button"
          [type]="type()"
          [disabled]="unavailable()"
          (click)="onClick()"
        >
          <ng-container *ngTemplateOutlet="content" />
        </button>
      }
    } @else {
      @if (hasLink()) {
        <a
          mat-button
          class="action-button"
          [routerLink]="linkDestination()"
          [queryParams]="queryParams()"
          [disabled]="unavailable()"
          [attr.aria-disabled]="unavailable() ? 'true' : null"
          [attr.tabindex]="unavailable() ? -1 : null"
          [attr.aria-label]="ariaLabel() || null"
          [class.action-button--unavailable]="unavailable()"
          (click)="onClick()"
        >
          <ng-container *ngTemplateOutlet="content" />
        </a>
      } @else {
        <button
          mat-button
          class="action-button"
          [type]="type()"
          [disabled]="unavailable()"
          (click)="onClick()"
        >
          <ng-container *ngTemplateOutlet="content" />
        </button>
      }
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
  readonly variant = input<'primary' | 'secondary' | 'text'>('primary');
  readonly type = input<'button' | 'submit'>('button');
  readonly disabled = input(false);
  readonly loading = input(false);
  readonly fullWidthOnMobile = input(false);
  readonly routerLink = input<RouterLink['routerLink'] | undefined>(undefined);
  readonly queryParams = input<Params | null>(null);
  readonly ariaLabel = input<string | null>(null);

  readonly hasLink = computed(() => this.routerLink() !== undefined && this.routerLink() !== null);
  readonly unavailable = computed(() => this.disabled() || this.loading());
  readonly linkDestination = computed(() => this.unavailable() ? null : this.routerLink());

  readonly activated = output<void>();

  onClick(): void {
    if (!this.unavailable()) this.activated.emit();
  }
}
