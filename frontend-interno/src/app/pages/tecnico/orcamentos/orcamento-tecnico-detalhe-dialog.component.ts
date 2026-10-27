import { NgClass } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY } from 'rxjs';
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
  imports: [DialogShellComponent, MatDialogModule, MatProgressSpinnerModule, NgClass],
  templateUrl: './orcamento-tecnico-detalhe-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OrcamentoTecnicoDetalheDialogComponent {
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
