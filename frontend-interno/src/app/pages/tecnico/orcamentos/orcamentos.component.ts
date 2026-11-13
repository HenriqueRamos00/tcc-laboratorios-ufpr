import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { combineLatest, EMPTY, merge, Subject } from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  map,
  shareReplay,
  startWith,
  switchMap,
  tap,
  withLatestFrom,
} from 'rxjs/operators';

import {
  apresentarOrcamentoTecnico,
  OPCOES_STATUS_TECNICO,
} from '@/app/model/orcamento-tecnico-presentation';
import {
  OrcamentoTecnico,
  PaginaOrcamentosTecnicos,
  StatusTecnicoOrcamento,
} from '@/app/model/orcamento-tecnico';
import { OrcamentosTecnicosService } from '@/app/services/orcamentos-tecnicos.service';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EmptyStateComponent } from '@/app/shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@/app/shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@/app/shared/components/search-field/search-field.component';
import { OrcamentoTecnicoDetalheDialogComponent } from './orcamento-tecnico-detalhe-dialog.component';

const TAMANHO_PAGINA_PADRAO = 20;
const OPCOES_TAMANHO_PAGINA = [10, 20, 50, 100];

type ErroDatas = 'formato' | 'ordem';

function dataIsoValida(data: string | null): boolean {
  if (data === null) return true;

  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(data);
  if (!partes) return false;

  const ano = Number(partes[1]);
  const mes = Number(partes[2]);
  const dia = Number(partes[3]);
  if (ano < 1 || ano > 9999 || mes < 1 || mes > 12 || dia < 1 || dia > 31) return false;

  const dataUtc = new Date(0);
  dataUtc.setUTCHours(0, 0, 0, 0);
  dataUtc.setUTCFullYear(ano, mes - 1, dia);
  return dataUtc.getUTCFullYear() === ano &&
    dataUtc.getUTCMonth() === mes - 1 &&
    dataUtc.getUTCDate() === dia;
}

function erroNasDatas(from: string | null, to: string | null): ErroDatas | null {
  if (!dataIsoValida(from) || !dataIsoValida(to)) return 'formato';
  if (from && to && from > to) return 'ordem';
  return null;
}

interface FiltrosOrcamentosTecnicos {
  readonly search: string;
  readonly status: StatusTecnicoOrcamento | null;
  readonly from: string | null;
  readonly to: string | null;
}

interface PedidoPagina {
  readonly filtros: FiltrosOrcamentosTecnicos;
  readonly pageIndex: number;
  readonly pageSize: number;
}

function criarPaginadorEmPortugues(): MatPaginatorIntl {
  const paginator = new MatPaginatorIntl();
  paginator.itemsPerPageLabel = 'Itens por página:';
  paginator.nextPageLabel = 'Próxima página';
  paginator.previousPageLabel = 'Página anterior';
  paginator.firstPageLabel = 'Primeira página';
  paginator.lastPageLabel = 'Última página';
  paginator.getRangeLabel = (page, pageSize, length) => {
    if (length === 0 || pageSize === 0) return `0 de ${length}`;
    const start = page * pageSize + 1;
    const end = Math.min(start + pageSize - 1, length);
    return `${start}-${end} de ${length}`;
  };
  return paginator;
}

@Component({
  selector: 'app-orcamentos-tecnicos',
  standalone: true,
  imports: [
    MatButtonModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    NgClass,
    ReactiveFormsModule,
    EmptyStateComponent,
    PageHeaderComponent,
    SearchFieldComponent,
  ],
  providers: [{ provide: MatPaginatorIntl, useFactory: criarPaginadorEmPortugues }],
  templateUrl: './orcamentos.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrcamentosTecnicosComponent {
  private readonly orcamentosService = inject(OrcamentosTecnicosService);
  private readonly dialog = inject(AppDialogService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pedidosPagina$ = new Subject<{ pageIndex: number; pageSize: number }>();
  private readonly tentarNovamente$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<StatusTecnicoOrcamento | 'ALL'>('ALL', {
    nonNullable: true,
  });
  readonly fromControl = new FormControl('', { nonNullable: true });
  readonly toControl = new FormControl('', { nonNullable: true });
  readonly statusOptions = OPCOES_STATUS_TECNICO;
  readonly orcamentos = signal<readonly OrcamentoTecnico[]>([]);
  readonly linhas = computed(() => this.orcamentos().map(apresentarOrcamentoTecnico));
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly erroDatas = signal<ErroDatas | null>(null);
  readonly intervaloDatasInvalido = computed(() => this.erroDatas() !== null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(TAMANHO_PAGINA_PADRAO);
  readonly totalItems = signal(0);
  readonly tamanhosPagina = OPCOES_TAMANHO_PAGINA;
  readonly breadcrumbs = [
    { label: 'Painel técnico', route: '/tecnico' },
    { label: 'Orçamentos' },
  ] as const;

  constructor() {
    const search$ = this.searchControl.valueChanges.pipe(
      map((search) => search.trim()),
      debounceTime(300),
      startWith(this.searchControl.value.trim()),
      distinctUntilChanged(),
    );
    const status$ = this.statusControl.valueChanges.pipe(
      startWith(this.statusControl.value),
      distinctUntilChanged(),
    );
    const from$ = this.fromControl.valueChanges.pipe(
      startWith(this.fromControl.value),
      distinctUntilChanged(),
    );
    const to$ = this.toControl.valueChanges.pipe(
      startWith(this.toControl.value),
      distinctUntilChanged(),
    );

    const filtros$ = combineLatest([search$, status$, from$, to$]).pipe(
      map(([search, status, from, to]): FiltrosOrcamentosTecnicos => ({
        search,
        status: status === 'ALL' ? null : status,
        from: from || null,
        to: to || null,
      })),
      distinctUntilChanged((anterior, atual) =>
        anterior.search === atual.search &&
        anterior.status === atual.status &&
        anterior.from === atual.from &&
        anterior.to === atual.to,
      ),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    const pedidosPorFiltro$ = filtros$.pipe(
      map((filtros): PedidoPagina => ({
        filtros,
        pageIndex: 0,
        pageSize: this.pageSize(),
      })),
    );
    const pedidosPorPaginacao$ = this.pedidosPagina$.pipe(
      withLatestFrom(filtros$),
      map(([pagina, filtros]): PedidoPagina => ({
        filtros,
        pageIndex: pagina.pageIndex,
        pageSize: pagina.pageSize,
      })),
    );
    const pedidosPorRepeticao$ = this.tentarNovamente$.pipe(
      withLatestFrom(filtros$),
      map(([, filtros]): PedidoPagina => ({
        filtros,
        pageIndex: this.pageIndex(),
        pageSize: this.pageSize(),
      })),
    );

    merge(pedidosPorFiltro$, pedidosPorPaginacao$, pedidosPorRepeticao$).pipe(
      switchMap((pedido) => {
        this.pageIndex.set(pedido.pageIndex);
        this.pageSize.set(pedido.pageSize);
        this.carregando.set(true);
        this.erro.set(false);
        this.erroPermissao.set(false);
        this.erroDatas.set(erroNasDatas(pedido.filtros.from, pedido.filtros.to));

        if (this.intervaloDatasInvalido()) {
          this.orcamentos.set([]);
          this.totalItems.set(0);
          this.carregando.set(false);
          return EMPTY;
        }

        return this.orcamentosService.list({
          ...pedido.filtros,
          page: pedido.pageIndex + 1,
          pageSize: pedido.pageSize,
        }).pipe(
          tap((pagina) => this.aplicarPagina(pagina)),
          catchError((error: HttpErrorResponse) => {
            this.orcamentos.set([]);
            this.totalItems.set(0);
            this.erro.set(true);
            this.erroPermissao.set(error.status === 403);
            return EMPTY;
          }),
          finalize(() => this.carregando.set(false)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  onPageChange(event: PageEvent): void {
    this.pedidosPagina$.next({ pageIndex: event.pageIndex, pageSize: event.pageSize });
  }

  tentarNovamente(): void {
    this.tentarNovamente$.next();
  }

  abrirDetalhes(orcamento: OrcamentoTecnico): void {
    this.dialog.openLateral<
      OrcamentoTecnicoDetalheDialogComponent,
      { readonly quoteId: string; readonly quoteCode: string }
    >(
      OrcamentoTecnicoDetalheDialogComponent,
      { quoteId: orcamento.id, quoteCode: orcamento.code },
      `Detalhes técnicos do orçamento ${orcamento.code}`,
    );
  }

  private aplicarPagina(pagina: PaginaOrcamentosTecnicos): void {
    this.orcamentos.set(pagina.items);
    this.pageIndex.set(pagina.page - 1);
    this.pageSize.set(pagina.pageSize);
    this.totalItems.set(pagina.totalItems);
  }
}
