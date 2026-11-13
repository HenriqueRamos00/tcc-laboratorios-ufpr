import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AppDialogService } from '@/app/services/app-dialog.service';
import {
  ProcessStatus,
  TestDetail,
  TestStatusAction,
  TestStatusHistoryEntry,
  TestStatusTarget,
} from '@/app/model/ensaio';
import { formatCivilDate, processStatusLabel } from '@/app/model/ensaio-presentation';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { TestReportSummary } from '@/app/model/relatorio';
import { ActionButtonComponent } from '@shared/components/action-button/action-button.component';
import { StatusProgressComponent, StatusProgressStep } from '@shared/components/status-progress/status-progress.component';
import { StatusJustificativaDialogComponent, StatusJustificativaDialogData } from './status-justificativa-dialog.component';

const FLOW_STATUSES: Exclude<ProcessStatus, 'cancelled'>[] = [
  'incomplete',
  'complete',
  'validated',
  'finalized',
  'drafting-report',
  'report-published',
];

const FLOW_STATUS_ICONS: Readonly<Record<Exclude<ProcessStatus, 'cancelled'>, string>> = {
  incomplete: 'science',
  complete: 'task_alt',
  validated: 'autorenew',
  finalized: 'task_alt',
  'drafting-report': 'description',
  'report-published': 'cloud_done',
};

const FLOW_STATUS_STEPS: readonly StatusProgressStep[] = FLOW_STATUSES.map((status) => ({
  key: status,
  label: processStatusLabel(status),
  icon: FLOW_STATUS_ICONS[status],
}));

@Component({
  selector: 'app-ensaio-status',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    RouterLink,
    ActionButtonComponent,
    StatusProgressComponent,
  ],
  templateUrl: './ensaio-status.component.html',
  styleUrl: './ensaio-status.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnsaioStatusComponent {
  private readonly service = inject(EnsaiosService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(AppDialogService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly id = this.route.snapshot.paramMap.get('id');

  readonly flowStatuses = FLOW_STATUSES;
  readonly targetStatuses = computed(() => FLOW_STATUSES.filter(
    (status): status is TestStatusTarget => status !== 'report-published' || this.publishedDocumentAvailable(),
  ));
  readonly flowSteps = computed(() => {
    const savedStatus = this.detail()?.status;
    return FLOW_STATUS_STEPS.filter((step) => step.key !== 'report-published'
      || this.publishedDocumentAvailable() || savedStatus === 'report-published');
  });
  readonly detail = signal<TestDetail | null>(null);
  readonly history = signal<TestStatusHistoryEntry[]>([]);
  readonly reportDocuments = signal<TestReportSummary[]>([]);
  readonly publishedDocumentAvailable = signal(false);
  readonly selectedStatus = signal<TestStatusTarget | null>(null);
  readonly detailLoading = signal(true);
  readonly detailError = signal(false);
  readonly detailErrorStatus = signal(0);
  readonly historyLoading = signal(true);
  readonly historyError = signal(false);
  readonly reportsLoading = signal(true);
  readonly reportsError = signal(false);
  readonly preparingReport = signal(false);
  readonly preparationError = signal('');
  readonly saving = signal(false);
  readonly conflict = signal(false);
  readonly staleState = signal(false);
  readonly saveError = signal('');
  readonly saveMessage = signal('');
  readonly currentStageIndex = computed(() => {
    const status = this.detail()?.status;
    if (!status || status === 'cancelled') return null;
    const index = FLOW_STATUSES.indexOf(status);
    return index < 0 ? null : index;
  });
  readonly formatDate = formatCivilDate;
  readonly statusLabel = processStatusLabel;

  constructor() {
    this.loadDetail();
    this.loadHistory();
    this.loadReports();
  }

  get canChangeStatus(): boolean {
    const current = this.detail();
    return !!current && !this.detailLoading() && !this.saving() &&
      !this.conflict() && !this.staleState();
  }

  get canSaveSelectedStatus(): boolean {
    const current = this.detail();
    const target = this.selectedStatus();
    return this.canChangeStatus && !!current && !!target &&
      current.status !== target;
  }

  get canPreparePreviousStage(): boolean {
    return this.canChangeStatus && this.adjacentTargetStatus(-1) !== null;
  }

  get canPrepareNextStage(): boolean {
    return this.canChangeStatus && this.adjacentTargetStatus(1) !== null;
  }

  get canPrepareReport(): boolean {
    return this.canChangeStatus && this.detail()?.status === 'drafting-report' &&
      !this.reportsLoading() && this.reportDocuments().length === 0 && !this.preparingReport();
  }

  prepareReport(): void {
    if (!this.id || !this.canPrepareReport) return;
    this.preparingReport.set(true);
    this.preparationError.set('');
    this.service.prepareReport(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.preparingReport.set(false);
        this.reportDocuments.set(result.reports);
        if (result.reportId) {
          this.openReport(result.reportId);
        } else if (result.outcome === 'selection-required') {
          this.preparationError.set('Há mais de um relatório para este ensaio. Selecione um dos documentos existentes.');
        }
        this.loadReports();
      },
      error: (error: HttpErrorResponse) => {
        this.preparingReport.set(false);
        this.preparationError.set(error.status === 409
          ? 'O estado do ensaio mudou. Os dados atuais foram recarregados.'
          : this.apiErrorMessage(error) ?? 'Não foi possível preparar o relatório. Confira sua conexão e tente novamente.');
        if (error.status === 409) {
          this.loadDetail();
          this.loadReports();
        }
      },
    });
  }

  openReport(reportId: string): void {
    void this.router.navigate(['/tecnico/relatorios', reportId]);
  }

  isCurrentStage(status: ProcessStatus): boolean {
    return this.detail()?.status === status;
  }

  selectStatus(status: ProcessStatus | null): void {
    if (!this.canChangeStatus) return;
    this.saveMessage.set('');
    if (!this.conflict() && !this.staleState()) this.saveError.set('');
    this.selectedStatus.set(status && !this.isCurrentStage(status) && this.isTargetStatus(status)
      ? status
      : null);
  }

  onStatusControlChange(event: Event): void {
    const value = (event.currentTarget as HTMLSelectElement).value;
    this.selectStatus(value ? value as ProcessStatus : null);
  }

  preparePreviousStage(): void {
    this.selectStatus(this.adjacentTargetStatus(-1));
  }

  prepareNextStage(): void {
    this.selectStatus(this.adjacentTargetStatus(1));
  }

  updateSelectedStatus(): void {
    const target = this.selectedStatus();
    if (!target || !this.canSaveSelectedStatus) return;
    this.updateStatus({ action: 'set-status', targetStatus: target });
  }

  reject(): void {
    const status = this.detail()?.status;
    if (!this.canChangeStatus || status === 'incomplete' || status === 'cancelled') return;
    this.openJustificationDialog('rejeitar', 'reject');
  }

  cancelTest(): void {
    if (!this.canChangeStatus || this.detail()?.status === 'cancelled') return;
    this.openJustificationDialog('cancelar', 'cancel');
  }

  retry(): void {
    if (this.detailError()) this.loadDetail();
    this.loadHistory();
  }

  reloadCurrentData(): void {
    this.conflict.set(false);
    this.staleState.set(false);
    this.saveError.set('');
    this.loadDetail();
    this.loadHistory();
  }

  formatTimestamp(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? value : date.toLocaleString('pt-BR');
  }

  private openJustificationDialog(
    acao: StatusJustificativaDialogData['acao'],
    action: Extract<TestStatusAction, 'reject' | 'cancel'>,
  ): void {
    const data: StatusJustificativaDialogData = { acao };
    this.dialog.open<StatusJustificativaDialogComponent, StatusJustificativaDialogData, string>(
      StatusJustificativaDialogComponent,
      data,
      acao === 'cancelar' ? 'Confirmar cancelamento do ensaio' : 'Informar justificativa da rejeição',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((justification) => {
      if (!justification || !this.canChangeStatus) return;
      this.updateStatus({ action, justification });
    });
  }

  private updateStatus(request: {
    action: 'set-status';
    targetStatus: TestStatusTarget;
  } | {
    action: 'reject' | 'cancel';
    justification: string;
  }): void {
    const current = this.detail();
    if (!this.id || !current || this.saving()) return;
    this.saving.set(true);
    this.saveError.set('');
    this.saveMessage.set('');
    this.conflict.set(false);
    this.staleState.set(false);
    this.service.updateStatus(this.id, { ...request, version: current.version })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.detail.update((item) => item
            ? { ...item, status: result.status, version: result.version }
            : item);
          this.selectedStatus.set(null);
          this.saving.set(false);
          this.conflict.set(false);
          this.staleState.set(false);
          this.saveMessage.set(result.changed
            ? current.status === 'cancelled'
              ? `Ensaio reaberto no status ${processStatusLabel(result.status)}. O histórico anterior foi preservado.`
              : current.status === 'report-published'
              ? `Status atualizado para ${processStatusLabel(result.status)}. Os dados existentes de publicação do laudo foram preservados.`
              : `Status atualizado para ${processStatusLabel(result.status)}.`
            : 'O ensaio já estava neste status.');
          this.loadHistory();
          this.loadReports();
        },
        error: (error: HttpErrorResponse) => {
          this.saving.set(false);
          if (error.status === 409) {
            if (error.error?.code === 'test-version-conflict') {
              this.conflict.set(true);
              this.saveError.set('Este ensaio foi alterado em outra sessão. Atualize os dados antes de fazer outra alteração.');
            } else {
              this.staleState.set(true);
              this.saveError.set(this.apiErrorMessage(error) ??
                'A alteração não pode ser aplicada ao estado atual do ensaio. Atualize os dados antes de tentar novamente.');
            }
          } else if (error.status === 403) {
            this.saveError.set('Sua conta não tem permissão para atualizar o status deste ensaio.');
          } else if (error.status === 400 || error.status === 422) {
            this.saveError.set('A API recusou a alteração. Confira a etapa e a justificativa e tente novamente.');
          } else if (error.status === 404) {
            this.saveError.set('O ensaio não está mais disponível.');
          } else {
            this.saveError.set('Não foi possível atualizar o status. Confira sua conexão e tente novamente.');
          }
        },
      });
  }

  private isTargetStatus(status: ProcessStatus): status is TestStatusTarget {
    return status !== 'cancelled' &&
      (status !== 'report-published' || this.publishedDocumentAvailable());
  }

  private adjacentTargetStatus(direction: -1 | 1): TestStatusTarget | null {
    const selectedIndex = this.selectedStatus()
      ? FLOW_STATUSES.indexOf(this.selectedStatus()!)
      : this.currentStageIndex();
    if (selectedIndex === null) return null;

    for (let index = selectedIndex + direction;
      index >= 0 && index < FLOW_STATUSES.length;
      index += direction) {
      const candidate = FLOW_STATUSES[index];
      if (candidate && this.isTargetStatus(candidate)) return candidate;
    }
    return null;
  }

  private apiErrorMessage(error: HttpErrorResponse): string | null {
    const detail = error.error?.detail;
    const message = error.error?.message;
    if (typeof detail === 'string' && detail.trim()) return detail.trim();
    if (typeof message === 'string' && message.trim()) return message.trim();
    return null;
  }

  private loadDetail(): void {
    if (!this.id) {
      this.detailLoading.set(false);
      this.detailError.set(true);
      this.detailErrorStatus.set(404);
      return;
    }
    this.detailLoading.set(true);
    this.detailError.set(false);
    this.service.get(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.detailLoading.set(false);
        this.detailError.set(false);
      },
      error: (error: HttpErrorResponse) => {
        this.detailLoading.set(false);
        this.detailError.set(true);
        this.detailErrorStatus.set(error.status);
      },
    });
  }

  private loadHistory(): void {
    if (!this.id) {
      this.historyLoading.set(false);
      this.historyError.set(true);
      return;
    }
    this.historyLoading.set(true);
    this.historyError.set(false);
    this.service.statusHistory(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (items) => {
        this.history.set(items);
        this.historyLoading.set(false);
        this.historyError.set(false);
      },
      error: () => {
        this.historyLoading.set(false);
        this.historyError.set(true);
      },
    });
  }

  retryReports(): void {
    this.loadReports();
  }

  private loadReports(): void {
    if (!this.id) {
      this.reportsLoading.set(false);
      this.reportsError.set(true);
      return;
    }
    this.reportsLoading.set(true);
    this.reportsError.set(false);
    this.service.reportsForTest(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.reportDocuments.set(result.items);
        this.publishedDocumentAvailable.set(result.publishedDocumentAvailable);
        this.reportsLoading.set(false);
        this.reportsError.set(false);
      },
      error: () => {
        this.reportDocuments.set([]);
        this.publishedDocumentAvailable.set(false);
        this.reportsLoading.set(false);
        this.reportsError.set(true);
      },
    });
  }
}
