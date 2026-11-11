import { DOCUMENT } from '@angular/common';
import { OverlayModule, ConnectedPosition } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

export interface ReferenceSearchOption {
  id: string;
  label: string;
  description: string;
}

@Component({
  selector: 'app-reference-search',
  standalone: true,
  imports: [OverlayModule, ReactiveFormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './reference-search.component.html',
  styleUrl: './reference-search.component.css',
  host: { class: 'block min-w-0', '(focusout)': 'checkFocus($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReferenceSearchComponent {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly document = inject(DOCUMENT);
  readonly field = viewChild<ElementRef<HTMLInputElement>>('field');
  readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  readonly control = input.required<FormControl<string>>();
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  readonly placeholder = input('Pesquisar');
  readonly hint = input('');
  readonly emptyText = input('Nenhuma correspondência encontrada.');
  readonly options = input<ReferenceSearchOption[]>([]);
  readonly loading = input(false);
  readonly locked = input(false);
  readonly error = input(false);
  readonly page = input(1);
  readonly pages = input(1);
  readonly maxlength = input<number | null>(null);
  readonly monospace = input(false);
  readonly openOnInput = input(true);
  readonly selected = output<string>();
  readonly termChanged = output<string>();
  readonly pageChanged = output<number>();
  readonly retry = output<void>();
  readonly opened = signal(false);
  readonly positions: ConnectedPosition[] = [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
  ];

  onInput(value: string): void {
    this.termChanged.emit(value);
    if (this.openOnInput()) this.opened.set(true);
  }

  select(id: string): void {
    if (this.loading() || this.error() || this.control().disabled) return;
    this.field()?.nativeElement.focus();
    this.opened.set(false);
    this.selected.emit(id);
  }

  close(): void {
    this.field()?.nativeElement.focus();
    this.opened.set(false);
  }

  outside(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) this.opened.set(false);
  }

  checkFocus(event: FocusEvent): void {
    const next = event.relatedTarget;
    if (next instanceof Node &&
      (this.host.nativeElement.contains(next) || this.panel()?.nativeElement.contains(next))) {
      return;
    }
    this.opened.set(false);
  }

  inputKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') { this.opened.set(false); return; }
    if (event.key !== 'ArrowDown') return;
    event.preventDefault();
    this.opened.set(true);
    setTimeout(() => this.panel()?.nativeElement.querySelector<HTMLButtonElement>('button:not(:disabled)')?.focus());
  }

  panelKeydown(event: KeyboardEvent): void {
    if (event.key === 'Escape') { event.preventDefault(); this.close(); return; }
    if (event.key === 'Enter' && event.target instanceof HTMLButtonElement &&
      event.target.classList.contains('reference-option')) {
      event.preventDefault();
      event.target.click();
      return;
    }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    const buttons = Array.from(this.panel()!.nativeElement.querySelectorAll<HTMLButtonElement>('.reference-option:not(:disabled)'));
    if (!buttons.length) return;
    const index = buttons.indexOf(this.document.activeElement as HTMLButtonElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : index < 0 ?
      event.key === 'ArrowUp' ? buttons.length - 1 : 0 :
      (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
    event.preventDefault();
    buttons[next]?.focus();
  }
}
