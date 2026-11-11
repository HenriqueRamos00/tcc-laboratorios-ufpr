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
  AbstractControl,
  FormArray,
  FormBuilder,
  FormControl,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatSelectChange, MatSelectModule } from '@angular/material/select';
import { ReferenceSearchComponent, ReferenceSearchOption } from '@shared/components/reference-search/reference-search.component';
import { OrcamentoSelecaoComponent } from './orcamento-selecao.component';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { of, Subject, timer } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  map,
  startWith,
  switchMap,
  tap,
} from 'rxjs/operators';
import {
  AnalysisClassification,
  AnalysisType,
  AnalyzedEquipmentReference,
  InstrumentReference,
  Measurement,
  MeasurementResultKind,
  QuoteReference,
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
import { PageHeaderComponent } from '@/app/shared/components/page-header/page-header.component';

type ConflictType = 'version' | 'sample' | 'generic' | null;
type IdentificationMode = 'existing' | 'new' | null;

const obrigatorioSemEspacos = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && control.value.trim().length > 0
    ? null
    : { required: true };

const resultadoTextualValido = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' &&
  control.value.trim().length > 0 &&
  control.value.length <= 2000
    ? null
    : { textResult: true };

const quantidadeValida = (control: AbstractControl): ValidationErrors | null =>
  typeof control.value === 'string' && isValidQuantityInput(control.value)
    ? null
    : { quantity: true };

@Component({
  selector: 'app-ensaio-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    ReferenceSearchComponent,
    MatProgressSpinnerModule,
    PageHeaderComponent,
  ],
  templateUrl: './ensaio-form.component.html',
  styleUrl: './ensaio-form.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnsaioFormComponent {
  readonly formatDate = formatCivilDate;
  private readonly service = inject(EnsaiosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(AppDialogService);
  private readonly sampleRefresh$ = new Subject<void>();
  private readonly equipmentRefresh$ = new Subject<void>();
  readonly measurements = this.fb.array(
    [] as ReturnType<typeof this.measurementGroup>[],
  );
  readonly form = this.fb.group({
    sampleId: this.fb.control<string | null>(null),
    newSampleCode: this.fb.nonNullable.control('', [Validators.maxLength(80)]),
    newSampleDescription: this.fb.nonNullable.control('', [Validators.maxLength(255)]),
    analyzedEquipmentId: this.fb.control<string | null>(null),
    type: ['', [obrigatorioSemEspacos, Validators.maxLength(200)]],
    analysisType: [
      'physical-chemical' as AnalysisType | 'other',
      Validators.required,
    ],
    method: ['', [obrigatorioSemEspacos, Validators.maxLength(255)]],
    standard: ['', [obrigatorioSemEspacos, Validators.maxLength(255)]],
    scheduledDate: ['', Validators.required],
    responsibleId: ['', Validators.required],
    equipmentIds: this.fb.control<string[]>([]),
    analysisClassification: [null as AnalysisClassification],
    reportText: ['', Validators.maxLength(10000)],
    measurements: this.measurements,
  });

  readonly sampleSearch = new FormControl('', { nonNullable: true });
  readonly equipmentSearch = new FormControl('', { nonNullable: true });
  readonly selectedQuote = signal<QuoteReference | null>(null);
  readonly samples = signal<SampleReference[]>([]);
  readonly samplePage = signal(1);
  readonly sampleTotal = signal(0);
  readonly sampleLoading = signal(false);
  readonly sampleError = signal(false);
  readonly sampleErrorStatus = signal(0);
  readonly sampleChooserOpen = signal(true);
  readonly identificationMode = signal<IdentificationMode>(null);
  readonly currentSample = signal<SampleReference | null>(null);
  readonly confirmedNewSampleCode = signal<string | null>(null);
  readonly analyzedEquipment = signal<AnalyzedEquipmentReference[]>([]);
  readonly currentAnalyzedEquipment = signal<AnalyzedEquipmentReference | null>(null);
  readonly equipmentPage = signal(1);
  readonly equipmentTotal = signal(0);
  readonly equipmentLoading = signal(false);
  readonly equipmentError = signal(false);
  readonly technicians = signal<TechnicianReference[]>([]);
  readonly techniciansLoading = signal(true);
  readonly techniciansError = signal(false);
  readonly instruments = signal<InstrumentReference[]>([]);
  readonly instrumentsLoading = signal(true);
  readonly instrumentsError = signal(false);
  readonly detail = signal<TestDetail | null>(null);
  readonly detailLoading = signal(false);
  readonly detailErrorStatus = signal<number | null>(null);
  readonly templateLoading = signal(false);
  readonly templateError = signal(false);
  readonly saving = signal(false);
  readonly saveError = signal('');
  readonly saveSuccess = signal(false);
  readonly conflictType = signal<ConflictType>(null);
  readonly editId = signal<string | null>(null);
  readonly analysisTypes = ANALYSIS_TYPES;
  readonly classifications = ANALYSIS_CLASSIFICATIONS;
  readonly processStatusLabel = processStatusLabel;
  private loadedAnalysisType: AnalysisType | 'other' = 'physical-chemical';

  constructor() {
    const navigationState = this.router.getCurrentNavigation()?.extras.state;
    this.saveSuccess.set(navigationState?.['testSavedConfirmation'] === true);
    this.bindSampleSearch();
    this.bindEquipmentSearch();
    this.loadTechnicians();
    this.loadInstruments();

    const id = this.route.snapshot.paramMap.get('id');
    this.editId.set(id);
    if (id) {
      this.loadDetail(id);
    } else {
      this.detailLoading.set(false);
      this.loadTemplate('physical-chemical');
    }
  }

  get rows(): FormArray {
    return this.measurements;
  }

  get formLocked(): boolean {
    return this.saving() || this.detailLoading();
  }

  get selectedSample(): SampleReference | null {
    const id = this.form.controls.sampleId.value;
    if (!id) return null;
    return this.samples().find((sample) => sample.id === id) ??
      (this.currentSample()?.id === id ? this.currentSample() : null);
  }

  get equipmentOptions(): AnalyzedEquipmentReference[] {
    const current = this.currentAnalyzedEquipment();
    if (!current || this.analyzedEquipment().some((item) => item.id === current.id)) {
      return this.analyzedEquipment();
    }
    return [current, ...this.analyzedEquipment()];
  }

  get sampleOptions(): ReferenceSearchOption[] {
    return this.samples().map((item) => ({ id: item.id, label: item.code, description: item.description }));
  }

  get analyzedEquipmentOptions(): ReferenceSearchOption[] {
    return this.equipmentOptions.map((item) => ({ id: item.id, label: item.tag, description: `Série: ${item.serialNumber}` }));
  }

  selectSampleOption(id: string): void {
    const item = this.samples().find((sample) => sample.id === id);
    if (item) this.selectSample(item);
  }

  selectEquipmentOption(id: string): void {
    const item = this.equipmentOptions.find((equipment) => equipment.id === id);
    if (item) {
      this.selectAnalyzedEquipment(item);
      this.equipmentSearch.setValue(item.tag);
    }
  }

  clearAnalyzedEquipment(): void {
    this.selectAnalyzedEquipment(null);
    this.equipmentSearch.setValue('');
  }

  get instrumentOptions(): InstrumentReference[] {
    const known = this.detail()?.equipment ?? [];
    const present = new Set(this.instruments().map((item) => item.id));
    return [...known.filter((item) => !present.has(item.id)), ...this.instruments()];
  }

  get selectedQuoteLabel(): string {
    const quote = this.selectedQuote();
    return quote ? `${quote.code} - ${quote.companyName}` : '';
  }

  get samplePages(): number {
    return Math.max(1, Math.ceil(this.sampleTotal() / 20));
  }

  get equipmentPages(): number {
    return Math.max(1, Math.ceil(this.equipmentTotal() / 20));
  }

  get exactSampleCodeExists(): boolean {
    const code = this.form.controls.newSampleCode.value.trim();
    return !!code && this.samples().some(
      (sample) => sample.code === code,
    );
  }

  get canConfirmNewSample(): boolean {
    const code = this.form.controls.newSampleCode.value.trim();
    const description = this.form.controls.newSampleDescription.value.trim();
    return !!this.selectedQuote() &&
      code.length > 0 && code.length <= 80 &&
      description.length > 0 && description.length <= 255 &&
      !this.sampleLoading() && !this.sampleError() && !this.exactSampleCodeExists;
  }

  get hasReferences(): boolean {
    const responsibleId = this.form.controls.responsibleId.value;
    const responsibleLoaded = !this.techniciansError() &&
      (!this.techniciansLoading() || this.technicians().length > 0 ||
        this.detail()?.responsible.id === responsibleId);
    const identificationValid = this.identificationMode() === 'existing'
      ? !!this.selectedSample
      : this.identificationMode() === 'new' &&
        !!this.selectedQuote() &&
        this.confirmedNewSampleCode() === this.form.controls.newSampleCode.value.trim() &&
        this.form.controls.newSampleDescription.value.trim().length > 0;
    return !!this.selectedQuote() && responsibleLoaded && identificationValid;
  }

  private bindSampleSearch(): void {
    this.sampleSearch.valueChanges
      .pipe(
        startWith(this.sampleSearch.value),
        map((term) => term.trim()),
        distinctUntilChanged(),
        tap(() => {
          this.samplePage.set(1);
          this.sampleLoading.set(!!this.selectedQuote());
          this.sampleError.set(false);
        }),
        switchMap((term) => timer(term ? 300 : 0).pipe(
          switchMap(() => this.sampleRefresh$.pipe(
            startWith(undefined),
            switchMap(() => {
              const quote = this.selectedQuote();
              if (!quote) {
                this.samples.set([]);
                this.sampleTotal.set(0);
                this.sampleLoading.set(false);
                return of(null);
              }
              this.sampleLoading.set(true);
              this.sampleError.set(false);
              return this.service.samples(quote.id, term, this.samplePage(), 20).pipe(
                tap((page) => {
                  this.samples.set(page.items);
                  this.sampleTotal.set(page.totalItems);
                  this.sampleLoading.set(false);
                }),
                catchError((error: HttpErrorResponse) => {
                  this.sampleError.set(true);
                  this.sampleErrorStatus.set(error.status);
                  this.sampleLoading.set(false);
                  return of(null);
                }),
              );
            }),
          )),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private bindEquipmentSearch(): void {
    this.equipmentSearch.valueChanges
      .pipe(
        startWith(this.equipmentSearch.value),
        map((term) => term.trim()),
        distinctUntilChanged(),
        tap(() => {
          this.equipmentPage.set(1);
          this.equipmentLoading.set(!!this.selectedQuote());
          this.equipmentError.set(false);
        }),
        switchMap((term) => timer(term ? 300 : 0).pipe(
          switchMap(() => this.equipmentRefresh$.pipe(
            startWith(undefined),
            switchMap(() => {
              const quote = this.selectedQuote();
              if (!quote) {
                this.analyzedEquipment.set([]);
                this.equipmentTotal.set(0);
                this.equipmentLoading.set(false);
                return of(null);
              }
              this.equipmentLoading.set(true);
              this.equipmentError.set(false);
              return this.service.analyzedEquipment(
                quote.id, term, this.equipmentPage(), 20,
              ).pipe(
                tap((page) => {
                  this.analyzedEquipment.set(page.items);
                  this.equipmentTotal.set(page.totalItems);
                  this.equipmentLoading.set(false);
                }),
                catchError(() => {
                  this.equipmentError.set(true);
                  this.equipmentLoading.set(false);
                  this.updateFormDisabledState();
                  return of(null);
                }),
              );
            }),
          )),
        )),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private loadTechnicians(): void {
    this.techniciansLoading.set(true);
    this.techniciansError.set(false);
    this.service.technicians().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (items) => {
        this.technicians.set(items);
        this.techniciansLoading.set(false);
      },
      error: () => {
        this.techniciansError.set(true);
        this.techniciansLoading.set(false);
      },
    });
  }

  private loadInstruments(): void {
    this.instrumentsLoading.set(true);
    this.instrumentsError.set(false);
    this.service.instruments().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (items) => {
        this.instruments.set(items);
        this.instrumentsLoading.set(false);
      },
      error: () => {
        this.instrumentsError.set(true);
        this.instrumentsLoading.set(false);
      },
    });
  }

  private loadDetail(id: string): void {
    this.detailLoading.set(true);
    this.detailErrorStatus.set(null);
    this.updateFormDisabledState();
    this.service.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (item) => {
        this.detail.set(item);
        this.patchDetail(item);
        this.detailLoading.set(false);
        this.updateFormDisabledState();
      },
      error: (error: HttpErrorResponse) => {
        this.detailErrorStatus.set(error.status);
        this.detailLoading.set(false);
        this.updateFormDisabledState();
      },
    });
  }

  private patchDetail(item: TestDetail): void {
    const sample: SampleReference = {
      ...item.sample,
      quote: item.quote,
      analyzedEquipment: item.analyzedEquipment,
    };
    this.selectedQuote.set(item.quote);
    this.currentSample.set(sample);
    this.currentAnalyzedEquipment.set(item.analyzedEquipment);
    this.identificationMode.set('existing');
    this.sampleChooserOpen.set(false);
    this.sampleSearch.setValue('');
    this.equipmentSearch.setValue('');
    this.form.patchValue({
      sampleId: item.sample.id,
      newSampleCode: '',
      newSampleDescription: '',
      analyzedEquipmentId: item.analyzedEquipment?.id ?? null,
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
    this.loadedAnalysisType = item.analysisType ?? 'other';
    this.setMeasurements(item.measurements);
    this.confirmedNewSampleCode.set(null);
    this.sampleRefresh$.next();
    this.equipmentRefresh$.next();
    this.form.markAsPristine();
  }

  private updateFormDisabledState(): void {
    const locked = this.saving() || this.detailLoading();
    if (locked) {
      this.form.disable({ emitEvent: false });
    } else {
      this.form.enable({ emitEvent: false });
    }
    this.setControlDisabled(this.sampleSearch, locked || !this.selectedQuote());
    this.setControlDisabled(
      this.equipmentSearch,
      locked || !this.selectedQuote() || this.equipmentError(),
    );
  }

  private setControlDisabled(control: FormControl, disabled: boolean): void {
    if (disabled && control.enabled) control.disable({ emitEvent: false });
    else if (!disabled && control.disabled) control.enable({ emitEvent: false });
  }

  private measurementGroup(item: Measurement) {
    const group = this.fb.group({
      code: [item.code, [obrigatorioSemEspacos, Validators.maxLength(80)]],
      name: [item.name, [obrigatorioSemEspacos, Validators.maxLength(200)]],
      unit: [item.unit ?? '', Validators.maxLength(40)],
      method: [item.method ?? '', Validators.maxLength(255)],
      referenceLimit: [item.referenceLimit ?? '', Validators.maxLength(255)],
      resultKind: [item.result.kind],
      resultInput: [resultToInput(item.result)],
    });
    this.setResultValidators(group, item.result.kind);
    return group;
  }

  private setResultValidators(
    group: ReturnType<typeof this.measurementGroup>,
    kind: MeasurementResultKind,
  ): void {
    const control = group.controls.resultInput;
    if (kind === 'quantity') {
      control.setValidators([
        Validators.required,
        Validators.pattern(/^[+-]?\d+(?:[,.]\d+)?$/),
        quantidadeValida,
      ]);
    } else if (kind === 'text') {
      control.setValidators([resultadoTextualValido]);
    } else {
      control.clearValidators();
    }
    control.updateValueAndValidity({ emitEvent: false });
  }

  private setMeasurements(items: Measurement[]): void {
    this.measurements.clear();
    items.forEach((item) => this.measurements.push(this.measurementGroup(item)));
    this.measurements.markAsPristine();
  }

  private loadTemplate(type: Exclude<AnalysisType, null>): void {
    this.templateLoading.set(true);
    this.templateError.set(false);
    this.service.template(type).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.setMeasurements(response.measurements.map(blankMeasurement));
        this.loadedAnalysisType = type;
        this.templateLoading.set(false);
      },
      error: () => {
        this.templateError.set(true);
        this.templateLoading.set(false);
      },
    });
  }

  changeAnalysisType(raw: string): void {
    if (this.saving() || this.templateLoading()) return;
    const chosen: AnalysisType | 'other' = raw === 'other'
      ? 'other'
      : (raw as Exclude<AnalysisType, null>);
    if (chosen === this.loadedAnalysisType) {
      this.templateError.set(false);
      this.form.controls.analysisType.setValue(chosen, { emitEvent: false });
      return;
    }
    const hasEditedRows = this.measurements.dirty ||
      this.measurements.controls.some((row) => {
        const kind = row.get('resultKind')?.value as MeasurementResultKind;
        return isMeasurementFilled({
          result: resultFromInput(row.get('resultInput')?.value ?? '', kind),
        } as Measurement);
      });
    if (!hasEditedRows || this.measurements.length === 0) {
      this.applyAnalysisType(chosen);
      return;
    }
    const data: ConfirmacaoDialogData = {
      titulo: 'Substituir linhas de medição?',
      mensagem: 'A troca do formato substituirá as linhas e os valores, métodos ou referências que você editou. Deseja continuar?',
      textoConfirmar: 'Trocar formato',
    };
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent, data, 'Confirmar troca do formato de resultados',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) this.applyAnalysisType(chosen);
      else this.form.controls.analysisType.setValue(this.loadedAnalysisType, { emitEvent: false });
    });
  }

  private applyAnalysisType(chosen: AnalysisType | 'other'): void {
    this.templateError.set(false);
    this.form.controls.analysisType.setValue(chosen, { emitEvent: false });
    this.saveError.set('');
    this.saveSuccess.set(false);
    if (chosen === 'other') {
      this.measurements.clear();
      this.loadedAnalysisType = 'other';
    } else if (chosen === 'physical-chemical' || chosen === 'chromatography') {
      this.loadTemplate(chosen);
    }
  }

  retryTemplate(): void {
    const selected = this.form.controls.analysisType.value;
    if (selected === 'physical-chemical' || selected === 'chromatography') {
      this.loadTemplate(selected);
    }
  }

  addGenericMeasurement(): void {
    if (this.saving() || this.measurements.length >= 100) return;
    this.measurements.push(this.measurementGroup({
      code: `custom-${crypto.randomUUID()}`,
      name: '',
      unit: null,
      method: null,
      referenceLimit: null,
      result: { kind: 'not-performed' },
    }));
    this.measurements.markAsDirty();
  }

  removeMeasurement(index: number): void {
    if (this.saving()) return;
    this.measurements.removeAt(index);
    this.measurements.markAsDirty();
  }

  requestResultKindChange(index: number, event: MatSelectChange): void {
    event.source.value = this.measurements.at(index).get('resultKind')?.value;
    this.changeResultKind(index, event.value);
  }

  changeResultKind(index: number, rawKind: string): void {
    if (this.saving()) return;
    const row = this.measurements.at(index);
    const resultKind = row.get('resultKind');
    const input = row.get('resultInput');
    if (!resultKind || !input) return;
    const previous = resultKind.value as MeasurementResultKind;
    const next = rawKind as MeasurementResultKind;
    if (next === previous) return;
    const hasContent = (input.value ?? '').length > 0 && previous !== 'not-performed';
    const apply = () => {
      resultKind.setValue(next);
      input.setValue('');
      this.setResultValidators(row, next);
      row.markAsDirty();
    };
    if (!hasContent) {
      resultKind.setValue(next);
      this.setResultValidators(row, next);
      row.markAsDirty();
      return;
    }
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent,
      {
        titulo: 'Trocar o tipo de resultado?',
        mensagem: 'O valor atual não pode ser transferido para o novo tipo. Deseja limpar o conteúdo e continuar?',
        textoConfirmar: 'Trocar tipo',
      },
      'Confirmar troca do tipo de resultado',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) apply();
      else resultKind.setValue(previous, { emitEvent: false });
    });
  }

  toggleInstrument(id: string, checked: boolean): void {
    if (this.saving() || this.instrumentsError() || this.instrumentsLoading()) return;
    const selected = this.form.controls.equipmentIds.value ?? [];
    this.form.controls.equipmentIds.setValue(
      checked ? [...new Set([...selected, id])] : selected.filter((item) => item !== id),
    );
  }

  openQuoteChooser(): void {
    if (this.formLocked) return;
    this.dialog.openLateral<OrcamentoSelecaoComponent, QuoteReference | null, QuoteReference>(
      OrcamentoSelecaoComponent, this.selectedQuote(), 'Selecionar orçamento',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((quote) => {
      if (quote) this.selectQuote(quote);
    });
  }

  selectQuote(quote: QuoteReference): void {
    if (this.formLocked || quote.id === this.selectedQuote()?.id) return;
    const code = this.form.controls.newSampleCode.value.trim();
    const description = this.form.controls.newSampleDescription.value.trim();
    if (this.identificationMode() === 'new' && (code || description)) {
      this.confirmDiscard(
        'Trocar o orçamento?',
        'A identificação nova e o equipamento analisado ainda não salvos serão descartados. Deseja continuar?',
        () => this.applyQuote(quote),
      );
      return;
    }
    this.applyQuote(quote);
  }

  private applyQuote(quote: QuoteReference): void {
    this.selectedQuote.set(quote);
    this.identificationMode.set(null);
    this.sampleChooserOpen.set(true);
    this.currentSample.set(null);
    this.currentAnalyzedEquipment.set(null);
    this.confirmedNewSampleCode.set(null);
    this.form.patchValue({
      sampleId: null,
      newSampleCode: '',
      newSampleDescription: '',
      analyzedEquipmentId: null,
    });
    this.sampleSearch.setValue('');
    this.equipmentSearch.setValue('');
    this.samplePage.set(1);
    this.equipmentPage.set(1);
    this.sampleError.set(false);
    this.equipmentError.set(false);
    this.sampleRefresh$.next();
    this.equipmentRefresh$.next();
    this.updateFormDisabledState();
  }

  toggleSampleChooser(): void {
    if (this.formLocked) return;
    if (!this.sampleChooserOpen()) {
      this.sampleChooserOpen.set(true);
      this.identificationMode.set(null);
      this.form.controls.sampleId.setValue(null);
      this.currentSample.set(null);
      this.confirmedNewSampleCode.set(null);
      this.sampleSearch.setValue('');
    }
  }

  chooseExistingMode(): void {
    if (this.formLocked || !this.selectedQuote() || this.identificationMode() === 'existing') return;
    const code = this.form.controls.newSampleCode.value.trim();
    const description = this.form.controls.newSampleDescription.value.trim();
    if (this.identificationMode() === 'new' && (code || description)) {
      this.confirmDiscard(
        'Descartar a identificação nova?',
        'O código, a descrição e o equipamento analisado ainda não salvos serão descartados.',
        () => this.applyExistingMode(),
      );
      return;
    }
    this.applyExistingMode();
  }

  private applyExistingMode(): void {
    this.identificationMode.set('existing');
    this.currentSample.set(null);
    this.confirmedNewSampleCode.set(null);
    this.form.patchValue({
      sampleId: null,
      newSampleCode: '',
      newSampleDescription: '',
      analyzedEquipmentId: null,
    });
    this.sampleChooserOpen.set(true);
    this.sampleSearch.setValue('');
  }

  chooseNewMode(): void {
    if (this.formLocked || !this.selectedQuote() || this.identificationMode() === 'new') return;
    const oldSelection = this.identificationMode() === 'existing' && this.selectedSample;
    if (oldSelection) {
      this.confirmDiscard(
        'Informar outra identificação?',
        'A seleção atual será substituída neste ensaio. A amostra existente continuará vinculada a outras análises.',
        () => this.applyNewMode(),
      );
      return;
    }
    this.applyNewMode();
  }

  private applyNewMode(): void {
    const code = this.sampleSearch.value.trim();
    this.identificationMode.set('new');
    this.currentSample.set(null);
    this.confirmedNewSampleCode.set(null);
    this.currentAnalyzedEquipment.set(null);
    this.form.patchValue({
      sampleId: null,
      newSampleCode: code,
      newSampleDescription: '',
      analyzedEquipmentId: null,
    });
    this.sampleChooserOpen.set(true);
    this.sampleSearch.setValue(code);
  }

  confirmNewSample(): void {
    if (this.formLocked || !this.canConfirmNewSample) return;
    this.confirmedNewSampleCode.set(this.form.controls.newSampleCode.value.trim());
    this.identificationMode.set('new');
    this.sampleChooserOpen.set(false);
  }

  selectSample(sample: SampleReference): void {
    if (this.formLocked || this.sampleLoading() || this.sampleError() ||
        sample.quote.id !== this.selectedQuote()?.id) return;
    this.currentSample.set(sample);
    this.currentAnalyzedEquipment.set(sample.analyzedEquipment);
    this.identificationMode.set('existing');
    this.sampleChooserOpen.set(false);
    this.confirmedNewSampleCode.set(null);
    this.form.patchValue({
      sampleId: sample.id,
      newSampleCode: '',
      newSampleDescription: '',
      analyzedEquipmentId: sample.analyzedEquipment?.id ?? null,
    });
  }

  onNewSampleCodeChanged(value: string): void {
    if (this.formLocked) return;
    this.form.controls.newSampleCode.setValue(value, { emitEvent: false });
    this.confirmedNewSampleCode.set(null);
    this.sampleChooserOpen.set(true);
    this.sampleSearch.setValue(value);
  }

  selectAnalyzedEquipment(item: AnalyzedEquipmentReference | null): void {
    if (this.formLocked || this.equipmentError() || this.equipmentLoading()) return;
    this.currentAnalyzedEquipment.set(item);
    this.form.controls.analyzedEquipmentId.setValue(item?.id ?? null);
  }

  setSamplePage(page: number): void {
    if (this.formLocked || page < 1 || page > this.samplePages) return;
    this.samplePage.set(page);
    this.sampleRefresh$.next();
  }

  setEquipmentPage(page: number): void {
    if (this.formLocked || page < 1 || page > this.equipmentPages) return;
    this.equipmentPage.set(page);
    this.equipmentRefresh$.next();
  }

  retrySamples(): void {
    if (!this.formLocked && this.selectedQuote()) this.sampleRefresh$.next();
  }

  retryAnalyzedEquipment(): void {
    if (!this.formLocked && this.selectedQuote()) {
      this.equipmentError.set(false);
      this.updateFormDisabledState();
      this.equipmentRefresh$.next();
    }
  }

  retryTechnicians(): void {
    if (!this.formLocked) this.loadTechnicians();
  }

  retryInstruments(): void {
    if (!this.formLocked) this.loadInstruments();
  }

  retryDetail(): void {
    const id = this.editId();
    if (id && !this.saving()) this.loadDetail(id);
  }

  private confirmDiscard(title: string, message: string, apply: () => void): void {
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent,
      { titulo: title, mensagem: message, textoConfirmar: 'Continuar' },
      title,
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) apply();
    });
  }

  private focusFirstInvalidField(): void {
    const firstInvalid = !this.selectedQuote() ? '#quote-selector' : this.identificationMode() === 'new'
      ? !this.form.controls.newSampleCode.value.trim()
        ? '#new-sample-code'
        : !this.form.controls.newSampleDescription.value.trim()
          ? '#new-sample-description'
          : null
      : !this.selectedSample
        ? '#sample-search'
        : null;
    const formFields: Array<[boolean, string]> = [
      [this.form.controls.type.invalid, '#analysis-type-name'],
      [this.form.controls.method.invalid, '#analysis-method'],
      [this.form.controls.standard.invalid, '#analysis-standard'],
      [this.form.controls.scheduledDate.invalid, '#scheduled-date'],
      [this.form.controls.responsibleId.invalid, '#responsible-id'],
      [this.form.controls.reportText.invalid, '#report-text'],
    ];
    const invalidField = formFields.find(([invalid]) => invalid);
    const invalidMeasurement = this.measurements.controls.findIndex((row) => row.invalid);
    const target = firstInvalid ?? invalidField?.[1] ??
      (invalidMeasurement >= 0 ? `#measurement-name-${invalidMeasurement}` : null);
    const element = target ? document.querySelector<HTMLElement>(target) : null;
    element?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    element?.focus();
  }

  private identificationIsValid(): boolean {
    if (this.identificationMode() === 'existing') {
      return !!this.selectedSample && this.selectedSample.quote.id === this.selectedQuote()?.id;
    }
    if (this.identificationMode() !== 'new' || !this.selectedQuote()) return false;
    const code = this.form.controls.newSampleCode.value.trim();
    const description = this.form.controls.newSampleDescription.value.trim();
    return this.confirmedNewSampleCode() === code &&
      code.length > 0 && code.length <= 80 &&
      description.length > 0 && description.length <= 255;
  }

  private resultRequest(group: ReturnType<typeof this.measurementGroup>): Measurement['result'] {
    const raw = group.getRawValue();
    return resultFromInput(
      raw.resultInput ?? '',
      raw.resultKind as MeasurementResultKind,
    );
  }

  save(): void {
    if (this.saving()) return;
    if (this.detailLoading() || this.templateLoading() ||
        (this.identificationMode() === 'new' && this.sampleLoading())) {
      this.saveError.set('Aguarde o carregamento dos dados antes de salvar.');
      this.saveSuccess.set(false);
      return;
    }
    this.form.markAllAsTouched();
    this.measurements.markAllAsTouched();
    if (this.templateError()) {
      this.saveError.set('O modelo de resultados não foi carregado. Tente novamente antes de salvar.');
      this.saveSuccess.set(false);
      return;
    }
    if (!this.selectedQuote() || !this.identificationIsValid() ||
        this.form.invalid || this.measurements.invalid ||
        this.techniciansLoading() || this.techniciansError() ||
        !this.form.controls.responsibleId.value || this.measurements.length > 100) {
      this.saveError.set('Revise o orçamento, confirme a identificação e confira os campos obrigatórios.');
      this.saveSuccess.set(false);
      this.focusFirstInvalidField();
      return;
    }

    const value = this.form.getRawValue();
    const measurements: Measurement[] = this.measurements.controls.map((group) => {
      const raw = group.getRawValue();
      return {
        code: (raw.code ?? '').trim().toLowerCase(),
        name: (raw.name ?? '').trim(),
        unit: raw.unit?.trim() || null,
        method: raw.method?.trim() || null,
        referenceLimit: raw.referenceLimit?.trim() || null,
        result: this.resultRequest(group),
      };
    });
    const mode = this.identificationMode();
    const request: TestRequest = {
      sampleId: mode === 'existing' ? this.selectedSample?.id ?? null : null,
      newSample: mode === 'new' ? {
        quoteId: this.selectedQuote()!.id,
        code: (value.newSampleCode ?? '').trim(),
        description: (value.newSampleDescription ?? '').trim(),
        analyzedEquipmentId: value.analyzedEquipmentId ?? null,
      } : null,
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
    this.updateFormDisabledState();
    this.saveError.set('');
    this.saveSuccess.set(false);
    this.conflictType.set(null);
    const id = this.editId();
    const operation = id && this.detail()
      ? this.service.update(id, { ...request, version: this.detail()!.version })
      : this.service.create(request);
    operation.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (saved) => {
        this.detail.set(saved);
        this.patchDetail(saved);
        this.saving.set(false);
        this.updateFormDisabledState();
        this.saveSuccess.set(true);
        this.saveError.set('');
        if (!id) {
          void this.router.navigate(['/tecnico/ensaios', saved.id, 'editar'], {
            replaceUrl: true,
            state: { testSavedConfirmation: true },
          });
        }
      },
      error: (error: HttpErrorResponse) => {
        this.saving.set(false);
        this.updateFormDisabledState();
        const code = error.error?.code as string | undefined;
        const mensagemApi = typeof error.error?.message === 'string'
          ? error.error.message.trim()
          : '';
        this.conflictType.set(error.status === 409
          ? code === 'test-version-conflict'
            ? 'version'
            : code === 'sample-code-conflict'
              ? 'sample'
              : 'generic'
          : null);
        this.saveError.set(error.status === 409
          ? code === 'sample-code-conflict'
            ? 'Este código de amostra já foi cadastrado. Pesquise uma referência existente ou escolha outro código.'
            : code === 'test-version-conflict'
              ? 'Este ensaio foi alterado em outra sessão. Seus dados continuam no formulário.'
              : mensagemApi || 'O salvamento encontrou um conflito. Seus dados continuam no formulário.'
          : error.status === 400
            ? 'A API recusou alguns dados. Revise os campos e tente novamente.'
            : error.status === 404
              ? 'Uma das referências selecionadas não está mais disponível. Confira as seleções.'
              : 'Não foi possível salvar. Confira a conexão e tente novamente.');
        this.saveSuccess.set(false);
      },
    });
  }

  openSampleSearchAfterConflict(): void {
    if (this.formLocked) return;
    this.identificationMode.set('existing');
    this.sampleChooserOpen.set(true);
    this.sampleSearch.setValue(this.form.controls.newSampleCode.value.trim());
    this.samplePage.set(1);
    this.sampleRefresh$.next();
  }

  reloadCurrent(): void {
    const id = this.editId();
    if (id && !this.saving()) this.loadDetail(id);
  }

  cancel(): void {
    if (!this.saving()) void this.router.navigate(['/tecnico/ensaios']);
  }

  openStatus(): void {
    const id = this.editId();
    if (!id || !this.detail() || this.formLocked) return;

    const navigateToStatus = (): void => {
      const currentId = this.editId();
      if (!currentId || !this.detail() || this.formLocked) return;
      void this.router.navigate(['/tecnico/ensaios', currentId, 'status']);
    };

    if (!this.hasUnsavedChanges()) {
      navigateToStatus();
      return;
    }

    const data: ConfirmacaoDialogData = {
      titulo: 'Descartar alterações do ensaio?',
      mensagem: 'Este ensaio tem alterações não salvas, incluindo o texto técnico em rascunho. Se continuar editando, os dados serão mantidos. Se confirmar, essas alterações serão descartadas antes de abrir o acompanhamento.',
      textoCancelar: 'Continuar editando',
      textoConfirmar: 'Descartar e alterar status',
      perigo: true,
    };
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent,
      data,
      'Confirmar descarte das alterações do ensaio',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) navigateToStatus();
    });
  }

  private hasUnsavedChanges(): boolean {
    const detail = this.detail();
    if (!detail) return this.form.dirty || this.measurements.dirty;

    const value = this.form.getRawValue();
    const equipmentIds = value.equipmentIds ?? [];
    const savedEquipmentIds = detail.equipmentIds;
    const equipmentSelectionChanged = equipmentIds.length !== savedEquipmentIds.length ||
      equipmentIds.some((id) => !savedEquipmentIds.includes(id));

    return this.form.dirty || this.measurements.dirty ||
      this.selectedQuote()?.id !== detail.quote.id ||
      this.identificationMode() !== 'existing' ||
      value.sampleId !== detail.sample.id ||
      value.newSampleCode !== '' ||
      value.newSampleDescription !== '' ||
      value.analyzedEquipmentId !== (detail.analyzedEquipment?.id ?? null) ||
      value.type !== detail.type ||
      value.analysisType !== (detail.analysisType ?? 'other') ||
      value.method !== detail.method ||
      value.standard !== detail.standard ||
      value.scheduledDate !== detail.scheduledDate ||
      value.responsibleId !== detail.responsible.id ||
      equipmentSelectionChanged ||
      value.analysisClassification !== detail.analysisClassification ||
      value.reportText !== (detail.reportText ?? '');
  }

  canDeactivate(): boolean {
    return !this.saving();
  }

}
