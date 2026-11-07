import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { API_URL } from '@/app/environment/env';
import type { Equipment } from '@/app/model/equipment';

import { EquipmentListComponent } from './equipment-list.component';

/**
 * As três procedências que a coluna "TAG" tem que atender, e nenhum valor saiu do cadastro
 * real: TAG e fabricante são inventados, porque dez linhas reais carregam nome de cliente
 * dentro de `NumSerie` e de `Tag`.
 */
const LIST: Equipment[] = [
  {
    id: 'NTcxMjg5',
    serialNumber: '571289',
    tag: '571289',
    equipmentTag: 'TR-00001',
    type: 'TRANSFORMADOR',
    location: null,
    company: 'EMPRESA DE ENERGIA',
    identification: 'LACTEC-02272/2026',
    reportCount: 4,
    mostRecentReportDate: '2026-06-23',
    nameplateKnown: true,
  },
  {
    id: 'ODg0MTIw',
    serialNumber: '884120',
    tag: '884120',
    equipmentTag: null,
    type: null,
    location: null,
    company: 'EMPRESA DE ENERGIA',
    identification: 'LACTEC-02465/2026',
    reportCount: 1,
    mostRecentReportDate: '2026-05-11',
    nameplateKnown: false,
  },
  {
    id: 'MDE0MzkvMjAyNi0wMDQ',
    serialNumber: null,
    tag: 'LACTEC-02820/2026',
    equipmentTag: null,
    type: null,
    location: null,
    company: 'EMPRESA DE ENERGIA',
    identification: 'LACTEC-02820/2026',
    reportCount: 1,
    mostRecentReportDate: '2026-04-02',
    nameplateKnown: false,
  },
];

function montarListagem() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });

  const fixture = TestBed.createComponent(EquipmentListComponent);
  fixture.detectChanges();

  TestBed.inject(HttpTestingController).expectOne(`${API_URL}/api/equipments`).flush(LIST);
  fixture.detectChanges();
  return fixture;
}

function celulas(fixture: ReturnType<typeof montarListagem>, coluna: number): string[] {
  const tela: HTMLElement = fixture.nativeElement;
  return Array.from(tela.querySelectorAll('tbody tr')).map(
    (linha) => linha.querySelectorAll('td')[coluna].textContent?.trim() ?? '',
  );
}

describe('EquipmentListComponent', () => {
  /**
   * A coluna de TAG prefere a TAG de placa e cai na identificação do laudo. Imprimir só
   * `tag` deixaria a coluna mostrando o número de série nas 55% de linhas em que o cadastro
   * tem a TAG de verdade, que é o defeito que esta fatia existe para corrigir.
   */
  it('mostra a TAG de placa quando o cadastro a informa, e cai no rótulo do laudo quando não', fakeAsync(() => {
    const fixture = montarListagem();

    expect(celulas(fixture, 1)).toEqual(['TR-00001', '884120', 'LACTEC-02820/2026']);
  }));

  /**
   * Tipo continua anunciando ausência campo a campo, e não equipamento a equipamento: o
   * cadastro não conhece 14% das séries e deixa `TipoEquipamento` em branco em 762 linhas.
   */
  it('anuncia a ausência do tipo apenas na linha que não tem tipo', fakeAsync(() => {
    const fixture = montarListagem();

    expect(celulas(fixture, 2)).toEqual(['TRANSFORMADOR', 'Não informado', 'Não informado']);
  }));

  /**
   * Localização não existe no cadastro: são dez colunas e nenhuma delas é lugar. A coluna
   * anuncia ausência em toda linha, inclusive na que tem placa completa.
   */
  it('anuncia a ausência da localização em toda linha, inclusive com placa completa', fakeAsync(() => {
    const fixture = montarListagem();

    expect(celulas(fixture, 3)).toEqual(['Não informado', 'Não informado', 'Não informado']);
  }));

  /**
   * A busca é por número de série e por TAG, e TAG tem duas procedências. Buscar só em `tag`
   * não encontraria pela TAG de placa, que é a que o cliente tem pintada no equipamento e vê
   * impressa na coluna.
   */
  it('encontra pela TAG de placa, que não é o rótulo do laudo', fakeAsync(() => {
    const fixture = montarListagem();
    const tela: HTMLElement = fixture.nativeElement;
    const campo = tela.querySelector('input') as HTMLInputElement;

    campo.value = 'tr-00001';
    campo.dispatchEvent(new Event('input'));
    tick();
    fixture.detectChanges();

    expect(celulas(fixture, 0)).toEqual(['571289']);
  }));

  it('continua encontrando pelo número de série', fakeAsync(() => {
    const fixture = montarListagem();
    const tela: HTMLElement = fixture.nativeElement;
    const campo = tela.querySelector('input') as HTMLInputElement;

    campo.value = '884120';
    campo.dispatchEvent(new Event('input'));
    tick();
    fixture.detectChanges();

    expect(celulas(fixture, 0)).toEqual(['884120']);
  }));
});
