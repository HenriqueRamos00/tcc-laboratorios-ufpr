import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal, computed } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY } from 'rxjs';
import { MatDialogRef } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { EnsaiosService } from '@/app/services/ensaios.service';
import { TestSummary } from '@/app/model/ensaio';
import { ActionButtonComponent } from '@shared/components/action-button/action-button.component';
import { catchError, finalize, tap } from 'rxjs/operators';

import { formatarDataCriacaoTecnica, apresentarStatusTecnico } from '@/app/model/orcamento-tecnico-presentation';
import { OrcamentoTecnicoDetalhe } from '@/app/model/orcamento-tecnico';
import { OrcamentosTecnicosService } from '@/app/services/orcamentos-tecnicos.service';
import { DialogShellComponent } from '@/app/shared/components/dialog-shell/dialog-shell.component';

export interface OrcamentoTecnicoDetalheDialogData {
  readonly quoteId: string;
  readonly quoteCode: string;
}

@Component({
  selector: 'app-orcamento-tecnico-detalhe-dialog',
  standalone: true,
  imports: [MatButtonModule, ActionButtonComponent, DialogShellComponent, MatDialogModule, MatProgressSpinnerModule, NgClass],
  templateUrl: './orcamento-tecnico-detalhe-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrcamentoTecnicoDetalheDialogComponent {
  private readonly ensaiosService = inject(EnsaiosService);
  private readonly router = inject(Router);
  private readonly dialogRef = inject(MatDialogRef);
  readonly ensaios = signal<TestSummary[]>([]);
  readonly totalEnsaios = signal(0);
  readonly carregandoEnsaios = signal(true);
  readonly erroEnsaios = signal(false);
  readonly acaoEnsaio = computed(() => this.totalEnsaios() === 0
    ? 'Criar ensaio' : this.totalEnsaios() === 1 ? 'Ver ensaio' : 'Ver ensaios');
  private readonly service = inject(OrcamentosTecnicosService);
  private readonly destroyRef = inject(DestroyRef);
  readonly data = inject<OrcamentoTecnicoDetalheDialogData>(MAT_DIALOG_DATA);
  readonly orcamento = signal<OrcamentoTecnicoDetalhe | null>(null);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly erroNaoEncontrado = signal(false);
  readonly formatarData = formatarDataCriacaoTecnica;

  constructor() {
    this.carregar();
    this.carregarEnsaios();
  }

  carregarEnsaios(): void {
    this.carregandoEnsaios.set(true);
    this.erroEnsaios.set(false);
    this.ensaiosService.list('', 1, 2, this.data.quoteId).pipe(
      tap((pagina) => {
        this.ensaios.set(pagina.items);
        this.totalEnsaios.set(pagina.totalItems);
      }),
      catchError(() => { this.erroEnsaios.set(true); return EMPTY; }),
      finalize(() => this.carregandoEnsaios.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  async abrirEnsaios(): Promise<void> {
    if (this.carregandoEnsaios() || this.erroEnsaios() || !this.orcamento()) return;
    const quantidade = this.totalEnsaios();
    const destino = quantidade === 0 ? ['/tecnico/ensaios/novo']
      : quantidade === 1 ? ['/tecnico/ensaios', this.ensaios()[0].id, 'editar']
      : ['/tecnico/ensaios'];
    const navegou = await this.router.navigate(destino, quantidade === 1 ? {} : {
      queryParams: { quoteId: this.data.quoteId, quoteCode: this.data.quoteCode },
    });
    if (navegou) this.dialogRef.close();
  }

  statusApresentacao(status: OrcamentoTecnicoDetalhe['technicalStatus']) {
    return apresentarStatusTecnico(status);
  }

  carregar(): void {
    this.carregando.set(true);
    this.erro.set(false);
    this.erroPermissao.set(false);
    this.erroNaoEncontrado.set(false);

    this.service.get(this.data.quoteId).pipe(
      tap((detalhe) => this.orcamento.set(detalhe)),
      catchError((error: HttpErrorResponse) => {
        this.erro.set(true);
        this.erroPermissao.set(error.status === 403);
        this.erroNaoEncontrado.set(error.status === 404);
        return EMPTY;
      }),
      finalize(() => this.carregando.set(false)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }
}
