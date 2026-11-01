import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { catchError, distinctUntilChanged, map, of, startWith, Subject, switchMap, tap, timer } from 'rxjs';
import { QuoteReference } from '@/app/model/ensaio';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';

@Component({
  selector: 'app-orcamento-selecao',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, DialogShellComponent],
  templateUrl: './orcamento-selecao.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrcamentoSelecaoComponent {
  private readonly service = inject(EnsaiosService);
  private readonly destroyRef = inject(DestroyRef);
  readonly dialogRef = inject(MatDialogRef<OrcamentoSelecaoComponent, QuoteReference>);
  readonly current = inject<QuoteReference | null>(MAT_DIALOG_DATA);
  readonly search = new FormControl('', { nonNullable: true });
  readonly items = signal<QuoteReference[]>([]);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly page = signal(1);
  readonly total = signal(0);
  private readonly refresh$ = new Subject<void>();

  constructor() {
    this.search.valueChanges.pipe(
      startWith(''), map((term) => term.trim()), distinctUntilChanged(),
      tap(() => { this.page.set(1); this.loading.set(true); this.error.set(false); }),
      switchMap((term) => timer(term ? 300 : 0).pipe(
        switchMap(() => this.refresh$.pipe(
          startWith(undefined),
          switchMap(() => {
            this.loading.set(true);
            this.error.set(false);
            return this.service.quotes(term, this.page(), 20).pipe(
              tap((result) => { this.items.set(result.items); this.total.set(result.totalItems); this.loading.set(false); }),
              catchError(() => { this.error.set(true); this.loading.set(false); return of(null); }),
            );
          }),
        )),
      )),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  get pages(): number { return Math.max(1, Math.ceil(this.total() / 20)); }

  setPage(page: number): void {
    if (this.loading() || this.error() || page < 1 || page > this.pages) return;
    this.page.set(page);
    this.refresh$.next();
  }

  retry(): void { this.refresh$.next(); }

  select(quote: QuoteReference): void {
    if (!this.loading() && !this.error()) this.dialogRef.close(quote);
  }
}
