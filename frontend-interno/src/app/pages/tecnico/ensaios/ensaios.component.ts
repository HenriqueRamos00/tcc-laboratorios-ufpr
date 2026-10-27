import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { of, Subject } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  startWith,
  switchMap,
  tap,
} from 'rxjs/operators';
import { TestSummary } from '@/app/model/ensaio';
import {
  formatCivilDate,
  processStatusLabel,
} from '@/app/model/ensaio-presentation';
import { EnsaiosService } from '@/app/services/ensaios.service';

@Component({
  selector: 'app-ensaios',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
  ],
  templateUrl: './ensaios.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnsaiosComponent {
  private readonly service = inject(EnsaiosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh$ = new Subject<void>();
  readonly search = new FormControl('', { nonNullable: true });
  readonly items = signal<TestSummary[]>([]);
  readonly page = signal(1);
  readonly pageSize = 20;
  readonly total = signal(0);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly forbidden = signal(false);
  constructor() {
    this.search.valueChanges
      .pipe(
        startWith(''),
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => this.page.set(1)),
        switchMap((term) =>
          this.refresh$.pipe(
            startWith(undefined),
            switchMap(() => {
              this.loading.set(true);
              this.error.set(false);
              this.forbidden.set(false);
              return this.service.list(term, this.page(), this.pageSize).pipe(
                tap((result) => {
                  this.items.set(result.items);
                  this.total.set(result.totalItems);
                }),
                catchError((err: HttpErrorResponse) => {
                  this.error.set(true);
                  this.forbidden.set(err.status === 403);
                  this.items.set([]);
                  return of(null);
                }),
                finalize(() => this.loading.set(false)),
              );
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }
  get pages(): number {
    return Math.max(1, Math.ceil(this.total() / this.pageSize));
  }
  setPage(next: number): void {
    if (next < 1 || next > this.pages) return;
    this.page.set(next);
    this.refresh$.next();
  }
  retry(): void {
    this.refresh$.next();
  }
  statusLabel = processStatusLabel;
  formatDate = formatCivilDate;
}
