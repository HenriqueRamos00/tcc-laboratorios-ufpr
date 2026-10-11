import { Injectable } from '@angular/core';
import { delay, Observable, of, throwError } from 'rxjs';

import { DEMO_DATA_ENABLED } from '@/app/environment/env';

import type {
  DetalheDoEquipamento,
  Equipamento,
  RelatorioDoEquipamento,
} from '@/app/model/equipment';

import {
  detalheDoEquipamento,
  EQUIPAMENTOS,
  historicoDoEquipamento,
} from './fixtures/equipamentos.fixture';

const LATENCIA_SIMULADA = 320;

@Injectable({ providedIn: 'root' })
export class EquipmentsService {
  listar(): Observable<readonly Equipamento[]> {
    if (!DEMO_DATA_ENABLED) {
      return throwError(() => new Error('Equipamentos de demonstração estão desativados.'));
    }
    return of(EQUIPAMENTOS).pipe(delay(LATENCIA_SIMULADA));
  }

  buscarPorId(id: string): Observable<DetalheDoEquipamento> {
    if (!DEMO_DATA_ENABLED) {
      return throwError(() => new Error('Equipamentos de demonstração estão desativados.'));
    }
    const detalhe = detalheDoEquipamento(id);
    return detalhe
      ? of(detalhe).pipe(delay(LATENCIA_SIMULADA))
      : throwError(() => new Error(`Equipamento ${id} não encontrado`));
  }

  historicoDeRelatorios(id: string): Observable<readonly RelatorioDoEquipamento[]> {
    if (!DEMO_DATA_ENABLED) {
      return throwError(() => new Error('Equipamentos de demonstração estão desativados.'));
    }
    return of(historicoDoEquipamento(id)).pipe(delay(LATENCIA_SIMULADA));
  }
}
