import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, finalize, of, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { readableDate } from '@shared/brazilian-date';
import { handFileToTheBrowser } from '@shared/file-download';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';

import {
  ANALYSIS_TYPE_LABEL,
  REPORT_STATUS_LABEL,
  type EquipmentDetail,
  type EquipmentReport,
} from '@/app/model/equipment';
import { TAB_BY_ANALYSIS_TYPE, type HealthTab } from '@/app/model/health-navigation';
import { EquipmentsService } from '@/app/services/equipments.service';
import { ReportsService } from '@/app/services/reports.service';

interface InformationCard {
  readonly icon: string;
  readonly label: string;
  readonly value: string;
}

/**
 * Uma linha do histórico já resolvida para a tela: o rótulo que se lê, a aba
 * que o link abre e o nome do arquivo que o download salva. O template não
 * precisa saber traduzir nada disso.
 */
interface HistoryRow {
  readonly id: string;
  readonly date: string;
  readonly analysisType: string;
  readonly status: string;
  readonly tab: HealthTab;
  readonly hasFile: boolean;
  readonly fileName: string;
}

const PER_PAGE = 4;

const DOWNLOAD_FAILURE =
  'Não foi possível baixar o relatório agora. Tente novamente em instantes.';

/** O AutoLAB não tem cadastro de equipamento, então não tem placa nem localização. */
const NOT_REPORTED = 'Não informado';

function historyRow(report: EquipmentReport): HistoryRow {
  return {
    id: report.id,
    date: readableDate(report.date),
    analysisType: ANALYSIS_TYPE_LABEL[report.analysisType],
    status: REPORT_STATUS_LABEL[report.status],
    tab: TAB_BY_ANALYSIS_TYPE[report.analysisType],
    hasFile: !!report.reportUrl,
    fileName: `relatorio-${report.analysisType}-${report.date}.pdf`,
  };
}

@Component({
  selector: 'app-equipamento-detalhe',
  standalone: true,
  imports: [RouterLink, MatIconModule, PageHeaderComponent, EmptyStateComponent],
  templateUrl: './equipment-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentDetailComponent {
  /** Vem do parâmetro de rota, via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly equipments = inject(EquipmentsService);
  private readonly reports = inject(ReportsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly equipment = signal<EquipmentDetail | null>(null);
  protected readonly history = signal<readonly EquipmentReport[]>([]);
  protected readonly failed = signal(false);
  protected readonly page = signal(0);

  /** Identificador do relatório cujo arquivo está sendo buscado agora. */
  protected readonly downloading = signal<string | null>(null);
  protected readonly downloadFailure = signal('');

  protected readonly cards = computed<readonly InformationCard[]>(() => {
    const equipment = this.equipment();
    if (!equipment) return [];
    return [
      { icon: 'tag', label: 'Numero de Série', value: equipment.serialNumber ?? NOT_REPORTED },
      { icon: 'sell', label: 'TAG', value: equipment.tag },
      { icon: 'water_drop', label: 'Tipo do Oleo', value: equipment.oilType ?? NOT_REPORTED },
      { icon: 'location_on', label: 'Local', value: equipment.location ?? NOT_REPORTED },
      { icon: 'business_center', label: 'Empresa', value: equipment.company },
      { icon: 'bolt', label: 'Tensão', value: equipment.voltage ?? NOT_REPORTED },
      { icon: 'electric_meter', label: 'Potência', value: equipment.power ?? NOT_REPORTED },
    ];
  });

  protected readonly totalPages = computed(() =>
    Math.max(1, Math.ceil(this.history().length / PER_PAGE)),
  );

  protected readonly visible = computed<readonly HistoryRow[]>(() =>
    this.history()
      .slice(this.page() * PER_PAGE, (this.page() + 1) * PER_PAGE)
      .map(historyRow),
  );

  protected readonly range = computed(() => {
    const total = this.history().length;
    if (!total) return '0 de 0';
    const from = this.page() * PER_PAGE + 1;
    const to = Math.min(from + PER_PAGE - 1, total);
    return `${from}-${to} de ${total}`;
  });

  constructor() {
    // O router reaproveita a instância quando só o id muda, e ngOnInit não roda
    // de novo.
    const id = toObservable(this.id);

    id.pipe(
      tap(() => {
        this.equipment.set(null);
        this.failed.set(false);
        this.page.set(0);
        this.downloadFailure.set('');
      }),
      switchMap((value) =>
        this.equipments.findById(value).pipe(
          catchError(() => {
            this.failed.set(true);
            return EMPTY;
          }),
        ),
      ),
      takeUntilDestroyed(),
    ).subscribe((detail) => this.equipment.set(detail));

    id.pipe(
      switchMap((value) => this.equipments.reportHistory(value).pipe(catchError(() => of([])))),
      takeUntilDestroyed(),
    ).subscribe((list) => this.history.set(list));
  }

  protected previous(): void {
    this.page.update((current) => Math.max(0, current - 1));
  }

  protected next(): void {
    this.page.update((current) => Math.min(this.totalPages() - 1, current + 1));
  }

  protected download(row: HistoryRow): void {
    this.downloading.set(row.id);
    this.downloadFailure.set('');
    this.reports
      .downloadReport(row.id)
      .pipe(
        finalize(() => this.downloading.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (file) => handFileToTheBrowser(file, row.fileName),
        error: () => this.downloadFailure.set(DOWNLOAD_FAILURE),
      });
  }

}
