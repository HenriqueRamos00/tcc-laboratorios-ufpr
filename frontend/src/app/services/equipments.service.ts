import { Injectable } from '@angular/core';
import { delay, Observable, of, throwError } from 'rxjs';

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

// O AutoLAB ainda não expõe estes endpoints. As assinaturas já são as da versão
// HTTP, então a troca é substituir o corpo de cada método.
@Injectable({ providedIn: 'root' })
export class EquipmentsService {
  /** GET /api/equipments */
  listar(): Observable<readonly Equipamento[]> {
    return of(EQUIPAMENTOS).pipe(delay(LATENCIA_SIMULADA));
  }

  /** GET /api/equipments/{id} */
  buscarPorId(id: string): Observable<DetalheDoEquipamento> {
    const detalhe = detalheDoEquipamento(id);
    return detalhe
      ? of(detalhe).pipe(delay(LATENCIA_SIMULADA))
      : throwError(() => new Error(`Equipamento ${id} não encontrado`));
  }

  /** GET /api/equipments/{id}/reports */
  historicoDeRelatorios(id: string): Observable<readonly RelatorioDoEquipamento[]> {
    return of(historicoDoEquipamento(id)).pipe(delay(LATENCIA_SIMULADA));
  }
}
