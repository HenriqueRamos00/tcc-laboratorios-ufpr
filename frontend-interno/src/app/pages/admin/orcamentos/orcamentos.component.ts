import { Clipboard } from '@angular/cdk/clipboard';
import { CurrencyPipe, DatePipe, NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl, MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { combineLatest, EMPTY, merge, of, Subject, timer } from 'rxjs';
import {
  catchError,
  distinctUntilChanged,
  finalize,
  filter,
  map,
  shareReplay,
  startWith,
  switchMap,
  tap,
  withLatestFrom,
} from 'rxjs/operators';

import {
  classeStatusOrcamento,
  rotuloStatusOrcamento,
  STATUS_ORCAMENTO_CANONICO,
} from '@/app/model/orcamento-presentation';
import { Orcamento, PaginaOrcamentos } from '@/app/model/orcamento';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { OrcamentosService } from '@/app/services/orcamentos.service';
import { EmptyStateComponent } from '@/app/shared/components/empty-state/empty-state.component';
import { PageHeaderComponent } from '@/app/shared/components/page-header/page-header.component';
import { SearchFieldComponent } from '@/app/shared/components/search-field/search-field.component';
import { QuoteDetailDialogComponent } from './quote-detail-dialog.component';
import { QuoteFormDialogComponent, QuoteFormDialogData, QuoteFormResult } from './quote-form-dialog.component';

const TAMANHO_PAGINA_PADRAO = 20;
const OPÇÕES_TAMANHO_PAGINA = [10, 20, 50, 100];

interface FiltrosOrcamentos {
  readonly search: string;
  readonly status: string | null;
}

interface PedidoPagina {
  readonly filtros: FiltrosOrcamentos;
  readonly pageIndex: number;
  readonly pageSize: number;
  readonly debounce: boolean;
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
  selector: 'app-orcamentos',
  standalone: true,
  imports: [
    CurrencyPipe,
    DatePipe,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSnackBarModule,
    MatTooltipModule,
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
export class OrcamentosComponent {
  private readonly orcamentosService = inject(OrcamentosService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly clipboard = inject(Clipboard);
  private readonly destroyRef = inject(DestroyRef);
  private readonly pedidosPagina$ = new Subject<{ pageIndex: number; pageSize: number }>();
  private readonly tentarNovamente$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly statusControl = new FormControl<string | 'ALL'>('ALL', { nonNullable: true });
  readonly statusOptions = STATUS_ORCAMENTO_CANONICO.map((value) => ({
    value,
    label: rotuloStatusOrcamento(value),
  }));
  readonly orcamentos = signal<readonly Orcamento[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(TAMANHO_PAGINA_PADRAO);
  readonly totalItems = signal(0);
  readonly tamanhosPagina = OPÇÕES_TAMANHO_PAGINA;
  readonly breadcrumbs = [
    { label: 'Painel administrativo', route: '/admin' },
    { label: 'Orçamentos' },
  ] as const;
  readonly baixando = signal<ReadonlySet<string>>(new Set());
  readonly rotuloStatus = rotuloStatusOrcamento;
  readonly classeStatus = classeStatusOrcamento;

  constructor() {
    const filtros$ = combineLatest([
      this.searchControl.valueChanges.pipe(startWith(this.searchControl.value)),
      this.statusControl.valueChanges.pipe(startWith(this.statusControl.value)),
    ]).pipe(
      map(([search, status]): FiltrosOrcamentos => ({
        search: search.trim(),
        status: status === 'ALL' ? null : status,
      })),
      distinctUntilChanged((anterior, atual) =>
        anterior.search === atual.search && anterior.status === atual.status,
      ),
      shareReplay({ bufferSize: 1, refCount: true }),
    );

    const pedidosPorFiltro$ = filtros$.pipe(
      map((filtros, indice): PedidoPagina => ({
        filtros,
        pageIndex: 0,
        pageSize: this.pageSize(),
        debounce: indice > 0,
      })),
    );

    const pedidosPorPaginacao$ = this.pedidosPagina$.pipe(
      withLatestFrom(filtros$),
      map(([pagina, filtros]): PedidoPagina => ({
        filtros,
        pageIndex: pagina.pageIndex,
        pageSize: pagina.pageSize,
        debounce: false,
      })),
    );

    const pedidosPorRepeticao$ = this.tentarNovamente$.pipe(
      withLatestFrom(filtros$),
      map(([, filtros]): PedidoPagina => ({
        filtros,
        pageIndex: this.pageIndex(),
        pageSize: this.pageSize(),
        debounce: false,
      })),
    );

    merge(pedidosPorFiltro$, pedidosPorPaginacao$, pedidosPorRepeticao$).pipe(
      switchMap((pedido) => {
        this.pageIndex.set(pedido.pageIndex);
        this.pageSize.set(pedido.pageSize);
        this.carregando.set(true);
        this.erro.set(false);
        this.erroPermissao.set(false);
        const espera$ = pedido.debounce ? timer(300) : of(0);
        return espera$.pipe(
          switchMap(() => this.orcamentosService.list({
            search: pedido.filtros.search,
            status: pedido.filtros.status,
            page: pedido.pageIndex + 1,
            pageSize: pedido.pageSize,
          })),
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

  copiarCodigo(orcamento: Orcamento): void {
    const mensagem = this.clipboard.copy(orcamento.code)
      ? 'Número da proposta copiado.'
      : 'Não foi possível copiar o número da proposta.';
    this.snackBar.open(mensagem, 'Fechar', { duration: 4000 });
  }

  visualizar(orcamento: Orcamento): void {
    this.dialog.openLateral<QuoteDetailDialogComponent, { quoteId: string; quoteCode: string }>(
      QuoteDetailDialogComponent,
      { quoteId: orcamento.id, quoteCode: orcamento.code },
      `Detalhes do orçamento ${orcamento.code}`,
    );
  }

  novo(): void {
    const ref = this.dialog.openLateral<QuoteFormDialogComponent, QuoteFormDialogData, QuoteFormResult>(
      QuoteFormDialogComponent,
      { quoteId: null, quoteCode: null },
      'Novo orçamento',
    );
    ref.afterClosed().pipe(
      filter((resultado): resultado is QuoteFormResult => resultado !== undefined),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((resultado) => {
      this.pedidosPagina$.next({
        pageIndex: resultado.created ? 0 : this.pageIndex(),
        pageSize: this.pageSize(),
      });
      this.snackBar.open('Orçamento cadastrado com sucesso.', 'Fechar', { duration: 4000 });
    });
  }

  editar(orcamento: Orcamento): void {
    const ref = this.dialog.openLateral<QuoteFormDialogComponent, QuoteFormDialogData, QuoteFormResult>(
      QuoteFormDialogComponent,
      { quoteId: orcamento.id, quoteCode: orcamento.code },
      `Editar orçamento ${orcamento.code}`,
    );
    ref.afterClosed().pipe(
      filter((resultado): resultado is QuoteFormResult => resultado !== undefined),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(() => {
      this.pedidosPagina$.next({ pageIndex: this.pageIndex(), pageSize: this.pageSize() });
      this.snackBar.open('Orçamento atualizado com sucesso.', 'Fechar', { duration: 4000 });
    });
  }

  baixar(orcamento: Orcamento): void {
    if (!orcamento.hasDocument || this.baixando().has(orcamento.id)) return;

    this.baixando.update((atual) => new Set(atual).add(orcamento.id));
    this.orcamentosService.downloadDocument(orcamento.id).pipe(
      tap((documento) => this.salvarDocumento(documento, orcamento.code)),
      catchError((error: HttpErrorResponse) => {
        const mensagem = error.status === 404
          ? 'O PDF deste orçamento não está mais disponível.'
          : 'Não foi possível baixar o PDF. Tente novamente.';
        this.snackBar.open(mensagem, 'Fechar', { duration: 5000 });
        return EMPTY;
      }),
      finalize(() => this.baixando.update((atual) => {
        const proximo = new Set(atual);
        proximo.delete(orcamento.id);
        return proximo;
      })),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  private aplicarPagina(pagina: PaginaOrcamentos): void {
    this.orcamentos.set(pagina.items);
    this.pageIndex.set(pagina.page - 1);
    this.pageSize.set(pagina.pageSize);
    this.totalItems.set(pagina.totalItems);
  }

  private salvarDocumento(documento: Blob, codigo: string): void {
    const nome = codigo.replace(/[^a-zA-Z0-9._-]/g, '_');
    const url = URL.createObjectURL(documento);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${nome || 'orcamento'}.pdf`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}
