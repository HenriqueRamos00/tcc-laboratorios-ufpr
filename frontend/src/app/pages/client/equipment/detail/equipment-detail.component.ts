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
import { catchError, combineLatest, EMPTY, finalize, of, switchMap, tap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { readableDate } from '@shared/brazilian-date';
import { handFileToTheBrowser } from '@shared/file-download';
import { EmptyStateComponent } from '@shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@shared/components/page-header/page-header.component';
import { DataUnavailableComponent } from '@shared/components/data-unavailable/data-unavailable.component';
import { PaginationComponent } from '@shared/components/pagination/pagination.component';

import {
  ANALYSIS_TYPE_LABEL,
  REPORT_STATUS_LABEL,
  type EquipmentDetail,
  type EquipmentReport,
} from '@/app/model/equipment';
import { TAB_BY_ANALYSIS_TYPE, type HealthTab } from '@/app/model/health-navigation';
import type { Page } from '@/app/model/page';
import { EquipmentsService } from '@/app/services/equipments.service';

interface InformationCard {
  readonly icon: string;
  readonly label: string;
  readonly value: string | null;
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

const PER_PAGE = 10;

const DOWNLOAD_FAILURE =
  'Não foi possível baixar o relatório agora. Tente novamente em instantes.';

/**
 * O cadastro do AutoLAB preenche campo a campo, e nenhuma coluna dele é obrigatória. A
 * célula diz que não foi informado, em vez de ficar vazia como se o dado fosse em branco.
 */
const NOT_REPORTED = 'Não informado';

/**
 * Dito uma vez, e só quando o cadastro não conhece a série. Sem esta frase a tela repetiria
 * "Não informado" em sete cartões sem dizer por quê, e o cliente não teria como distinguir
 * um equipamento que o cadastro não tem de um cujas colunas estão em branco.
 */
const NAMEPLATE_UNKNOWN =
  'O cadastro de equipamentos do laboratório não tem este número de série, então os dados de placa não aparecem aqui.';

function historyRow(report: EquipmentReport): HistoryRow {
  return {
    id: report.id,
    date: readableDate(report.date),
    analysisType: ANALYSIS_TYPE_LABEL[report.analysisType],
    status: REPORT_STATUS_LABEL[report.status],
    tab: TAB_BY_ANALYSIS_TYPE[report.analysisType],
    hasFile: report.hasSignedDocument,
    fileName: `relatorio-${report.analysisType}-${report.date}.pdf`,
  };
}

@Component({
  selector: 'app-equipamento-detalhe',
  standalone: true,
  imports: [
    RouterLink,
    MatIconModule,
    PageHeaderComponent,
    EmptyStateComponent,
    DataUnavailableComponent,
    PaginationComponent,
  ],
  templateUrl: './equipment-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentDetailComponent {
  /** Vem do parâmetro de rota, via withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly equipments = inject(EquipmentsService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly equipment = signal<EquipmentDetail | null>(null);
  protected readonly history = signal<Page<EquipmentReport> | null>(null);
  protected readonly historyLoading = signal(false);
  protected readonly failed = signal(false);
  protected readonly page = signal(1);
  protected readonly perPage = PER_PAGE;

  /** Identificador do relatório cujo arquivo está sendo buscado agora. */
  protected readonly downloading = signal<string | null>(null);
  protected readonly downloadFailure = signal('');

  protected readonly cards = computed<readonly InformationCard[]>(() => {
    const equipment = this.equipment();
    if (!equipment) return [];
    return [
      { icon: 'tag', label: 'Número de Série', value: equipment.serialNumber ?? NOT_REPORTED },
      { icon: 'sell', label: 'TAG', value: equipment.equipmentTag ?? equipment.tag },
      { icon: 'category', label: 'Tipo de Equipamento', value: equipment.type ?? NOT_REPORTED },
      { icon: 'water_drop', label: 'Tipo do Óleo', value: equipment.oilType ?? NOT_REPORTED },
      { icon: 'location_on', label: 'Local', value: equipment.location },
      { icon: 'business_center', label: 'Empresa', value: equipment.company },
      { icon: 'bolt', label: 'Tensão', value: equipment.voltage ?? NOT_REPORTED },
      { icon: 'electric_meter', label: 'Potência', value: equipment.power ?? NOT_REPORTED },
      { icon: 'factory', label: 'Fabricante', value: equipment.manufacturer ?? NOT_REPORTED },
      {
        icon: 'calendar_month',
        label: 'Ano de Fabricação',
        value: equipment.manufacturingYear ?? NOT_REPORTED,
      },
      { icon: 'opacity', label: 'Volume de Óleo', value: equipment.oilVolume ?? NOT_REPORTED },
    ];
  });

  protected readonly nameplateNotice = computed(() =>
    this.equipment()?.nameplateKnown === false ? NAMEPLATE_UNKNOWN : '',
  );

  protected readonly visible = computed<readonly HistoryRow[]>(() =>
    (this.history()?.items ?? []).map(historyRow),
  );

  protected readonly historyTotal = computed(() => this.history()?.total ?? 0);

  constructor() {
    // O router reaproveita a instância quando só o id muda, e ngOnInit não roda
    // de novo.
    const id = toObservable(this.id);

    id.pipe(
      tap(() => {
        this.equipment.set(null);
        this.failed.set(false);
        this.history.set(null);
        this.page.set(1);
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

    combineLatest([id, toObservable(this.page)])
      .pipe(
        tap(() => this.historyLoading.set(true)),
        switchMap(([value, page]) =>
          this.equipments.reportHistory(value, { page, pageSize: PER_PAGE }).pipe(
            catchError(() =>
              of<Page<EquipmentReport>>({ items: [], total: 0, page, pageSize: PER_PAGE }),
            ),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        this.history.set(result);
        this.historyLoading.set(false);
      });
  }

  protected goToPage(page: number): void {
    this.downloadFailure.set('');
    this.page.set(page);
  }

  protected download(row: HistoryRow): void {
    const equipment = this.equipment();
    if (!equipment) return;
    this.downloading.set(row.id);
    this.downloadFailure.set('');
    this.equipments
      .reportDocument(equipment.id, row.id)
      .pipe(
        finalize(() => this.downloading.set(null)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (file) => handFileToTheBrowser(file.content, file.name ?? row.fileName),
        error: () => this.downloadFailure.set(DOWNLOAD_FAILURE),
      });
  }
}
