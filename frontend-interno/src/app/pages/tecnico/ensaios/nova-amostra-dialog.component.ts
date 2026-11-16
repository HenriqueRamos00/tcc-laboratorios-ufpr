import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { catchError, distinctUntilChanged, map, of, startWith, Subject, switchMap, tap, timer } from 'rxjs';
import { AnalyzedEquipmentReference, NewSampleRequest, QuoteReference, SampleReference } from '@/app/model/ensaio';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { DialogShellComponent } from '@shared/components/dialog-shell/dialog-shell.component';
import { ReferenceSearchComponent } from '@shared/components/reference-search/reference-search.component';

export interface NovaAmostraDialogData {
  quote: QuoteReference;
  code: string;
  description: string;
  equipment: AnalyzedEquipmentReference | null;
}

export type NovaAmostraDialogResult =
  | { sample: SampleReference }
  | { newSample: NewSampleRequest; equipment: AnalyzedEquipmentReference | null };

@Component({
  selector: 'app-nova-amostra-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, DialogShellComponent, ReferenceSearchComponent],
  templateUrl: './nova-amostra-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NovaAmostraDialogComponent {
  readonly data = inject<NovaAmostraDialogData>(MAT_DIALOG_DATA);
  readonly dialogRef = inject(MatDialogRef<NovaAmostraDialogComponent, NovaAmostraDialogResult>);
  private readonly service = inject(EnsaiosService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly form = this.fb.nonNullable.group({
    code: [this.data.code, [Validators.required, Validators.maxLength(80)]],
    description: [this.data.description, [Validators.required, Validators.maxLength(255)]],
  });
  readonly equipmentSearch = new FormControl('', { nonNullable: true });
  readonly equipment = signal(this.data.equipment);
  readonly equipmentItems = signal<AnalyzedEquipmentReference[]>([]);
  readonly equipmentLoading = signal(true);
  readonly equipmentError = signal(false);
  readonly equipmentPage = signal(1);
  readonly equipmentTotal = signal(0);
  readonly samples = signal<SampleReference[]>([]);
  readonly sampleLoading = signal(true);
  readonly sampleError = signal(false);
  readonly samplePage = signal(1);
  readonly sampleTotal = signal(0);
  private readonly sampleRefresh$ = new Subject<void>();
  private readonly equipmentRefresh$ = new Subject<void>();

  constructor() {
    this.form.controls.code.valueChanges.pipe(
      startWith(this.data.code), map((term) => term.trim()), distinctUntilChanged(),
      tap(() => { this.samplePage.set(1); this.sampleLoading.set(true); }),
      switchMap((term) => timer(term ? 300 : 0).pipe(
        switchMap(() => this.sampleRefresh$.pipe(startWith(undefined), switchMap(() => {
          this.sampleLoading.set(true);
          this.sampleError.set(false);
          return this.service.samples(this.data.quote.id, term, this.samplePage(), 20).pipe(
            tap((page) => { this.samples.set(page.items); this.sampleTotal.set(page.totalItems); this.sampleLoading.set(false); }),
            catchError(() => { this.sampleError.set(true); this.sampleLoading.set(false); return of(null); }),
          );
        }))),
      )), takeUntilDestroyed(this.destroyRef),
    ).subscribe();
    this.equipmentSearch.valueChanges.pipe(
      startWith(''), map((term) => term.trim()), distinctUntilChanged(),
      tap(() => { this.equipmentPage.set(1); this.equipmentLoading.set(true); }),
      switchMap((term) => timer(term ? 300 : 0).pipe(
        switchMap(() => this.equipmentRefresh$.pipe(startWith(undefined), switchMap(() => {
          this.equipmentLoading.set(true);
          this.equipmentError.set(false);
          return this.service.analyzedEquipment(this.data.quote.id, term, this.equipmentPage(), 20).pipe(
            tap((page) => { this.equipmentItems.set(page.items); this.equipmentTotal.set(page.totalItems); this.equipmentLoading.set(false); }),
            catchError(() => { this.equipmentError.set(true); this.equipmentLoading.set(false); return of(null); }),
          );
        }))),
      )), takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  get samplePages(): number { return Math.max(1, Math.ceil(this.sampleTotal() / 20)); }
  get equipmentPages(): number { return Math.max(1, Math.ceil(this.equipmentTotal() / 20)); }
  get sampleOptions() { return this.samples().map((item) => ({ id: item.id, label: item.code, description: item.description })); }
  get equipmentOptions() { return this.equipmentItems().map((item) => ({ id: item.id, label: item.tag, description: `Série: ${item.serialNumber}` })); }
  get duplicate(): boolean { return this.samples().some((item) => item.code === this.form.controls.code.value.trim()); }
  get canConfirm(): boolean {
    return this.form.valid && !!this.form.controls.code.value.trim() && !!this.form.controls.description.value.trim() &&
      !this.sampleLoading() && !this.sampleError() && !this.duplicate;
  }
  setSamplePage(page: number): void {
    if (page < 1 || page > this.samplePages) return;
    this.samplePage.set(page); this.retrySamples();
  }
  setEquipmentPage(page: number): void {
    if (page < 1 || page > this.equipmentPages) return;
    this.equipmentPage.set(page); this.retryEquipment();
  }
  retrySamples(): void { this.sampleRefresh$.next(); }
  retryEquipment(): void { this.equipmentRefresh$.next(); }
  selectSample(id: string): void {
    if (this.sampleLoading() || this.sampleError()) return;
    const sample = this.samples().find((item) => item.id === id);
    if (sample) this.dialogRef.close({ sample });
  }
  selectEquipment(id: string): void {
    if (this.equipmentLoading() || this.equipmentError()) return;
    const equipment = this.equipmentItems().find((item) => item.id === id);
    if (equipment) { this.equipment.set(equipment); this.equipmentSearch.setValue(equipment.tag); }
  }
  confirm(): void {
    if (!this.canConfirm) { this.form.markAllAsTouched(); return; }
    this.dialogRef.close({ newSample: {
      quoteId: this.data.quote.id,
      code: this.form.controls.code.value.trim(),
      description: this.form.controls.description.value.trim(),
      analyzedEquipmentId: this.equipment()?.id ?? null,
    }, equipment: this.equipment() });
  }
}
