import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormArray,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin, of, Subject, Subscription } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  startWith,
  switchMap,
  tap,
} from 'rxjs/operators';
import {
  AnalysisClassification,
  AnalysisType,
  InstrumentReference,
  Measurement,
  SampleReference,
  TechnicianReference,
  TestDetail,
  TestRequest,
} from '@/app/model/ensaio';
import {
  ANALYSIS_CLASSIFICATIONS,
  ANALYSIS_TYPES,
  blankMeasurement,
  formatCivilDate,
  isMeasurementFilled,
  isValidQuantityInput,
  processStatusLabel,
  resultFromInput,
  resultToInput,
} from '@/app/model/ensaio-presentation';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import {
  ConfirmacaoDialogComponent,
  ConfirmacaoDialogData,
} from '@shared/components/confirmacao-dialog/confirmacao-dialog.component';

@Component({
  selector: 'app-ensaio-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './ensaio-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnsaioFormComponent {
  formatDate = formatCivilDate;
  private readonly service = inject(EnsaiosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(AppDialogService);
  readonly measurements = this.fb.array(
    [] as ReturnType<typeof this.measurementGroup>[],
  );
  readonly form = this.fb.group({
    sampleId: ['', Validators.required],
    type: ['', [Validators.required, Validators.maxLength(200)]],
    analysisType: [
      'physical-chemical' as AnalysisType | 'other',
      Validators.required,
    ],
    method: ['', [Validators.required, Validators.maxLength(255)]],
    standard: ['', [Validators.required, Validators.maxLength(255)]],
    scheduledDate: ['', Validators.required],
    responsibleId: ['', Validators.required],
    equipmentIds: this.fb.control<string[]>([]),
    analysisClassification: [null as AnalysisClassification],
    reportText: ['', Validators.maxLength(10000)],
    measurements: this.measurements,
  });
  readonly samples = signal<SampleReference[]>([]);
  readonly currentSample = signal<SampleReference | null>(null);
  readonly samplePage = signal(1);
  readonly sampleTotal = signal(0);
  readonly sampleLoading = signal(false);
  readonly sampleFetchError = signal(false);
  readonly sampleSearch = new FormControl('', { nonNullable: true });
  private loadedAnalysisType: AnalysisType | 'other' = 'physical-chemical';
  readonly technicians = signal<TechnicianReference[]>([]);
  readonly instruments = signal<InstrumentReference[]>([]);
  readonly detail = signal<TestDetail | null>(null);
  readonly loading = signal(true);
  readonly templateLoading = signal(false);
  readonly templateError = signal(false);
  private templateSubscription: Subscription | null = null;
  readonly referenceError = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal('');
  readonly conflict = signal(false);
  readonly sampleError = signal(false);
  readonly editId = signal<string | null>(null);
  readonly analysisTypes = ANALYSIS_TYPES;
  readonly classifications = ANALYSIS_CLASSIFICATIONS;
  readonly processStatusLabel = processStatusLabel;
  constructor() {
    this.sampleSearch.valueChanges
      .pipe(
        startWith(''),
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => this.samplePage.set(1)),
        switchMap((term) =>
          this.sampleQuery$.pipe(
            startWith(undefined),
            switchMap(() => {
              this.sampleLoading.set(true);
              this.sampleFetchError.set(false);
              return this.service.samples(term, this.samplePage(), 20).pipe(
                catchError(() => {
                  this.sampleFetchError.set(true);
                  return of(null);
                }),
                finalize(() => this.sampleLoading.set(false)),
              );
            }),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        if (page) {
          this.samples.set(page.items);
          this.sampleTotal.set(page.totalItems);
        }
      });
    this.load();
  }
  get rows(): FormArray {
    return this.measurements;
  }
  get hasReferences(): boolean {
    const selected = this.selectedSample;
    return (
      this.technicians().length > 0 && (this.sampleTotal() > 0 || !!selected)
    );
  }
  get selectedSampleIsOutsidePage(): boolean {
    const selected = this.selectedSample;
    return (
      !!selected && !this.samples().some((sample) => sample.id === selected.id)
    );
  }
  get samplePages(): number {
    return Math.max(1, Math.ceil(this.sampleTotal() / 20));
  }
  setSamplePage(page: number): void {
    if (page < 1 || page > this.samplePages) return;
    this.samplePage.set(page);
    this.sampleQuery$.next();
  }
  private readonly sampleQuery$ = new Subject<void>();
  get selectedSample(): SampleReference | undefined {
    const id = this.form.controls.sampleId.value;
    return (
      this.samples().find((sample) => sample.id === id) ??
      (this.currentSample()?.id === id
        ? (this.currentSample() ?? undefined)
        : undefined)
    );
  }
  selectSample(id: string): void {
    const selected = this.samples().find((sample) => sample.id === id);
    this.currentSample.set(selected ?? null);
  }
  private load(): void {
    this.loading.set(true);
    this.referenceError.set(false);
    const id = this.route.snapshot.paramMap.get('id');
    this.editId.set(id);
    forkJoin({
      technicians: this.service.technicians(),
      instruments: this.service.instruments(),
      detail: id ? this.service.get(id) : of(null),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ technicians, instruments, detail }) => {
          this.technicians.set(technicians);
          this.instruments.set(instruments);
          this.detail.set(detail);
          if (detail) this.patchDetail(detail);
          else this.loadTemplate('physical-chemical');
        },
        error: (error: HttpErrorResponse) => {
          this.referenceError.set(true);
          this.sampleError.set(error.status === 403);
        },
      });
  }
  private patchDetail(item: TestDetail): void {
    this.currentSample.set({
      ...item.sample,
      quote: item.quote,
      analyzedEquipment: item.analyzedEquipment,
    });
    this.loadedAnalysisType = item.analysisType ?? 'other';
    this.form.patchValue({
      sampleId: item.sample.id,
      type: item.type,
      analysisType: item.analysisType ?? 'other',
      method: item.method,
      standard: item.standard,
      scheduledDate: item.scheduledDate,
      responsibleId: item.responsible.id,
      equipmentIds: item.equipmentIds,
      analysisClassification: item.analysisClassification,
      reportText: item.reportText ?? '',
    });
    this.setMeasurements(item.measurements);
  }
  private measurementGroup(item: Measurement) {
    return this.fb.group({
      code: [item.code, [Validators.required, Validators.maxLength(80)]],
      name: [item.name, [Validators.required, Validators.maxLength(200)]],
      unit: [item.unit ?? '', Validators.maxLength(40)],
      method: [item.method ?? '', Validators.maxLength(255)],
      referenceLimit: [item.referenceLimit ?? '', Validators.maxLength(255)],
      resultInput: [resultToInput(item.result)],
    });
  }
  private setMeasurements(items: Measurement[]): void {
    this.measurements.clear();
    items.forEach((item) =>
      this.measurements.push(this.measurementGroup(item)),
    );
    this.measurements.markAsPristine();
  }
  private loadTemplate(type: Exclude<AnalysisType, null>): void {
    this.cancelTemplateRequest();
    this.templateLoading.set(true);
    this.templateError.set(false);
    this.templateSubscription = this.service
      .template(type)
      .pipe(
        finalize(() => this.templateLoading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (response) => {
          this.setMeasurements(response.measurements.map(blankMeasurement));
          this.loadedAnalysisType = type;
          this.saveError.set('');
        },
        error: () => this.templateError.set(true),
      });
  }
  private cancelTemplateRequest(): void {
    this.templateSubscription?.unsubscribe();
    this.templateSubscription = null;
  }
  changeAnalysisType(raw: string): void {
    const chosen: AnalysisType | 'other' =
      raw === 'other' ? 'other' : (raw as Exclude<AnalysisType, null>);
    if (chosen === this.loadedAnalysisType) {
      this.cancelTemplateRequest();
      this.templateError.set(false);
      this.form.controls.analysisType.setValue(chosen, { emitEvent: false });
      return;
    }
    const hasEditedRows =
      this.measurements.dirty ||
      this.measurements.controls.some((row) =>
        isMeasurementFilled({
          result: resultFromInput(row.get('resultInput')?.value ?? ''),
        } as Measurement),
      );
    if (!hasEditedRows || this.measurements.length === 0) {
      this.applyAnalysisType(chosen);
      return;
    }
    const data: ConfirmacaoDialogData = {
      titulo: 'Substituir linhas de medição?',
      mensagem:
        'A troca do formato substituirá as linhas e os valores, métodos ou referências que você editou. Deseja continuar?',
      textoConfirmar: 'Trocar formato',
    };
    this.dialog
      .open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
        ConfirmacaoDialogComponent,
        data,
        'Confirmar troca do formato de resultados',
      )
      .afterClosed()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) this.applyAnalysisType(chosen);
        else
          this.form.controls.analysisType.setValue(this.loadedAnalysisType, {
            emitEvent: false,
          });
      });
  }
  private applyAnalysisType(chosen: AnalysisType | 'other'): void {
    this.templateError.set(false);
    this.form.controls.analysisType.setValue(chosen, { emitEvent: false });
    this.saveError.set('');
    if (chosen === 'other') {
      this.cancelTemplateRequest();
      this.setMeasurements([]);
      this.loadedAnalysisType = 'other';
    } else if (chosen === 'physical-chemical' || chosen === 'chromatography')
      this.loadTemplate(chosen);
  }
  retryTemplate(): void {
    const selected = this.form.controls.analysisType.value;
    if (selected === 'physical-chemical' || selected === 'chromatography')
      this.loadTemplate(selected);
  }
  addGenericMeasurement(): void {
    if (this.measurements.length >= 100) return;
    this.measurements.push(
      this.measurementGroup({
        code: `custom-${crypto.randomUUID()}`,
        name: '',
        unit: null,
        method: null,
        referenceLimit: null,
        result: { kind: 'not-performed' },
      }),
    );
    this.measurements.markAsDirty();
  }
  removeMeasurement(index: number): void {
    this.measurements.removeAt(index);
    this.measurements.markAsDirty();
  }
  toggleInstrument(id: string, checked: boolean): void {
    const selected = this.form.controls.equipmentIds.value ?? [];
    this.form.controls.equipmentIds.setValue(
      checked ? [...selected, id] : selected.filter((item) => item !== id),
    );
  }
  cancel(): void {
    if (!this.saving()) void this.router.navigate(['/tecnico/ensaios']);
  }
  canDeactivate(): boolean {
    return !this.saving();
  }
  retry(): void {
    this.load();
    this.sampleQuery$.next();
  }
  reloadCurrent(): void {
    const id = this.editId();
    if (!id) return;
    this.loading.set(true);
    this.service
      .get(id)
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (item) => {
          this.detail.set(item);
          this.patchDetail(item);
          this.conflict.set(false);
          this.saveError.set('');
        },
        error: () =>
          this.saveError.set(
            'Não foi possível recarregar a versão persistida.',
          ),
      });
  }
  private focusFirstInvalidField(): void {
    const formFields: Array<[boolean, string]> = [
      [this.form.controls.sampleId.invalid, '#sample-id'],
      [this.form.controls.type.invalid, '#analysis-type-name'],
      [this.form.controls.method.invalid, '#analysis-method'],
      [this.form.controls.standard.invalid, '#analysis-standard'],
      [this.form.controls.scheduledDate.invalid, '#scheduled-date'],
      [this.form.controls.responsibleId.invalid, '#responsible-id'],
      [this.form.controls.reportText.invalid, '#report-text'],
    ];
    const invalidField = formFields.find(([invalid]) => invalid);
    const invalidMeasurement = this.measurements.controls.findIndex(
      (row) => row.invalid,
    );
    const target =
      invalidField?.[1] ??
      (invalidMeasurement >= 0
        ? `#measurement-name-${invalidMeasurement}`
        : null);
    const element = target ? document.querySelector<HTMLElement>(target) : null;
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    element?.focus();
  }

  save(): void {
    if (this.saving()) return;
    if (this.loading() || this.templateLoading()) {
      this.saveError.set('Aguarde o carregamento dos dados antes de salvar.');
      return;
    }
    this.form.markAllAsTouched();
    this.measurements.markAllAsTouched();
    if (this.templateError()) {
      this.saveError.set(
        'O modelo de resultados não foi carregado. Tente novamente antes de salvar.',
      );
      return;
    }
    if (
      this.form.invalid ||
      this.measurements.invalid ||
      !this.hasReferences ||
      this.measurements.length > 100
    ) {
      this.saveError.set(
        'Revise os campos obrigatórios e as referências disponíveis.',
      );
      this.focusFirstInvalidField();
      return;
    }
    const value = this.form.getRawValue();
    const invalidQuantity = this.measurements.controls.some((row) => {
      const raw = row.get('resultInput')?.value ?? '';
      return (
        /^[+-]?\d+(?:[,.]\d+)?$/.test(raw.trim()) && !isValidQuantityInput(raw)
      );
    });
    if (invalidQuantity) {
      this.saveError.set(
        'A quantidade aceita até 16 dígitos inteiros e 8 casas decimais. Nenhum valor foi arredondado.',
      );
      return;
    }
    const measurements: Measurement[] = this.measurements.controls.map(
      (group) => {
        const raw = group.getRawValue();
        return {
          code: (raw.code ?? '').trim().toLowerCase(),
          name: (raw.name ?? '').trim(),
          unit: raw.unit?.trim() || null,
          method: raw.method?.trim() || null,
          referenceLimit: raw.referenceLimit?.trim() || null,
          result: resultFromInput(raw.resultInput ?? ''),
        };
      },
    );
    const request: TestRequest = {
      sampleId: value.sampleId ?? '',
      type: (value.type ?? '').trim(),
      analysisType: value.analysisType === 'other' ? null : value.analysisType,
      method: (value.method ?? '').trim(),
      standard: (value.standard ?? '').trim(),
      scheduledDate: value.scheduledDate ?? '',
      responsibleId: value.responsibleId ?? '',
      equipmentIds: value.equipmentIds ?? [],
      analysisClassification: value.analysisClassification,
      reportText: value.reportText || null,
      measurements,
    };
    this.saving.set(true);
    this.saveError.set('');
    this.conflict.set(false);
    const id = this.editId();
    const operation =
      id && this.detail()
        ? this.service.update(id, {
            ...request,
            version: this.detail()!.version,
          })
        : this.service.create(request);
    operation
      .pipe(
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (saved) => {
          this.detail.set(saved);
          this.saveError.set('Ensaio salvo com sucesso.');
          if (!id)
            void this.router.navigate(
              ['/tecnico/ensaios', saved.id, 'editar'],
              { replaceUrl: true },
            );
        },
        error: (error: HttpErrorResponse) => {
          this.conflict.set(error.status === 409);
          this.saveError.set(
            error.status === 409
              ? 'Este ensaio foi alterado por outra pessoa. Seus dados continuam no formulário.'
              : error.status === 400
                ? 'A API recusou alguns dados. Revise o formulário.'
                : 'Não foi possível salvar. Confira a conexão e tente novamente.',
          );
        },
      });
  }
}
