import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { combineLatest, EMPTY, of, Subject } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, finalize, startWith, switchMap, tap } from 'rxjs/operators';

import { Equipamento, EquipamentoRisco, EquipamentoTipo, EQUIPAMENTO_RISCOS, EQUIPAMENTO_TIPOS } from '@/app/model/equipamento';
import { AppDialogService } from '@/app/services/app-dialog.service';
import { EquipamentosService } from '@/app/services/equipamentos.service';
import { ConfirmacaoDialogComponent, ConfirmacaoDialogData } from '@shared/components/confirmacao-dialog/confirmacao-dialog.component';
import { EquipamentoFormDialogComponent, EquipamentoFormDialogData } from './equipamento-form-dialog.component';

@Component({
  selector: 'app-equipamentos',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSnackBarModule, MatTooltipModule, RouterLink],
  templateUrl: './equipamentos.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipamentosComponent {
  private readonly equipmentService = inject(EquipamentosService);
  private readonly dialog = inject(AppDialogService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);
  private readonly refresh$ = new Subject<void>();

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly typeControl = new FormControl<EquipamentoTipo | ''>('', { nonNullable: true });
  readonly riskControl = new FormControl<EquipamentoRisco | ''>('', { nonNullable: true });
  readonly tipos = EQUIPAMENTO_TIPOS;
  readonly riscos = EQUIPAMENTO_RISCOS;
  readonly equipamentos = signal<Equipamento[]>([]);
  readonly carregando = signal(true);
  readonly erro = signal(false);
  readonly erroPermissao = signal(false);
  readonly desativando = signal<ReadonlySet<number>>(new Set());

  constructor() {
    combineLatest([
      this.searchControl.valueChanges.pipe(startWith(this.searchControl.value), debounceTime(300), distinctUntilChanged()),
      this.typeControl.valueChanges.pipe(startWith(this.typeControl.value), distinctUntilChanged()),
      this.riskControl.valueChanges.pipe(startWith(this.riskControl.value), distinctUntilChanged()),
      this.refresh$.pipe(startWith(undefined)),
    ]).pipe(
      switchMap(([search, type, risk]) => {
        this.carregando.set(true);
        this.erro.set(false);
        this.erroPermissao.set(false);
        return this.equipmentService.list(search, type || null, risk || null).pipe(
          tap((equipamentos) => this.equipamentos.set(equipamentos)),
          catchError((error: HttpErrorResponse) => {
            this.equipamentos.set([]);
            this.erro.set(true);
            this.erroPermissao.set(error.status === 403);
            return of([] as Equipamento[]);
          }),
          finalize(() => this.carregando.set(false)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }

  tentarNovamente(): void {
    this.refresh$.next();
  }

  novoEquipamento(): void {
    this.abrirFormulario(null);
  }

  editarEquipamento(equipamento: Equipamento): void {
    this.abrirFormulario(equipamento);
  }

  tipoLabel(tipo: EquipamentoTipo): string {
    return this.tipos.find((item) => item.value === tipo)?.label ?? tipo;
  }

  riscoLabel(risco: EquipamentoRisco): string {
    return this.riscos.find((item) => item.value === risco)?.label ?? risco;
  }

  desativarEquipamento(equipamento: Equipamento): void {
    if (this.desativando().has(equipamento.id)) return;
    const data: ConfirmacaoDialogData = {
      titulo: 'Desativar equipamento?',
      mensagem: `O equipamento ${equipamento.tag} (${equipamento.serialNumber}) será removido dos resultados ativos.`,
      textoConfirmar: 'Desativar',
      perigo: true,
    };
    this.dialog.open<ConfirmacaoDialogComponent, ConfirmacaoDialogData, boolean>(
      ConfirmacaoDialogComponent, data, `Confirmar desativação de ${equipamento.tag}`, { width: '420px' },
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (!confirmed || this.desativando().has(equipamento.id)) return;
      this.desativando.update((atual) => new Set(atual).add(equipamento.id));
      this.equipmentService.deactivate(equipamento.id).pipe(
        catchError((error: HttpErrorResponse) => {
          this.snackBar.open(error.status === 403
            ? 'Sua conta não tem permissão para desativar equipamentos.'
            : 'Não foi possível desativar o equipamento. Tente novamente.', 'Fechar', { duration: 5000 });
          return EMPTY;
        }),
        finalize(() => this.desativando.update((atual) => {
          const proximo = new Set(atual);
          proximo.delete(equipamento.id);
          return proximo;
        })),
        takeUntilDestroyed(this.destroyRef),
      ).subscribe(() => {
        this.snackBar.open('Equipamento desativado.', 'Fechar', { duration: 3500 });
        this.refresh$.next();
      });
    });
  }

  private abrirFormulario(equipamento: Equipamento | null): void {
    this.dialog.openLateral<EquipamentoFormDialogComponent, EquipamentoFormDialogData, Equipamento>(
      EquipamentoFormDialogComponent, { equipamento },
      equipamento ? `Editar equipamento ${equipamento.tag}` : 'Cadastrar novo equipamento',
    ).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe((salvo) => {
      if (!salvo) return;
      this.snackBar.open(equipamento ? 'Equipamento atualizado.' : 'Equipamento cadastrado.', 'Fechar', { duration: 3500 });
      this.refresh$.next();
    });
  }
}
