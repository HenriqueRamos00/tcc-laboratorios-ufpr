import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import {
  PAGE_SIZE_WITHOUT_SCROLL_AREA,
  renderMeasuredTable,
} from '@shared/components/data-table/data-table.spec-util';

import { API_URL } from '@/app/environment/env';
import type { Equipment } from '@/app/model/equipment';

import { EquipmentListComponent, SEARCH_DEBOUNCE_MS } from './equipment-list.component';

const LIST_URL = `${API_URL}/api/equipments`;
const PAGE_SIZE = PAGE_SIZE_WITHOUT_SCROLL_AREA;

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

function urlDaPagina(page: number, search?: string): string {
  const busca = search === undefined ? '' : `&search=${encodeURIComponent(search)}`;
  return `${LIST_URL}?page=${page}&pageSize=${PAGE_SIZE}${busca}`;
}

function montarListagem(total = LIST.length) {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });

  const fixture = TestBed.createComponent(EquipmentListComponent);
  renderMeasuredTable(fixture);

  TestBed.inject(HttpTestingController)
    .expectOne(urlDaPagina(1))
    .flush(LIST, { headers: { 'X-Total-Count': String(total) } });
  fixture.detectChanges();
  return fixture;
}

function celulas(fixture: ReturnType<typeof montarListagem>, coluna: number): string[] {
  const tela: HTMLElement = fixture.nativeElement;
  return Array.from(tela.querySelectorAll('tbody tr[data-row]')).map(
    (linha) => linha.querySelectorAll('td')[coluna].textContent?.trim() ?? '',
  );
}

function digitar(fixture: ReturnType<typeof montarListagem>, texto: string): void {
  const campo = (fixture.nativeElement as HTMLElement).querySelector('input') as HTMLInputElement;
  campo.value = texto;
  campo.dispatchEvent(new Event('input'));
  fixture.detectChanges();
}

function esperarABusca(fixture: ReturnType<typeof montarListagem>): void {
  tick(SEARCH_DEBOUNCE_MS);
  renderMeasuredTable(fixture);
}

function clicarEmProximo(fixture: ReturnType<typeof montarListagem>): void {
  const tela: HTMLElement = fixture.nativeElement;
  const proximo = Array.from(tela.querySelectorAll('app-pagination button')).find((botao) =>
    botao.textContent?.includes('Próximo'),
  ) as HTMLButtonElement;
  proximo.click();
  renderMeasuredTable(fixture);
}

describe('EquipmentListComponent', () => {
  it('distingue integração pendente de lista vazia e de uma falha temporária', fakeAsync(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(EquipmentListComponent);
    renderMeasuredTable(fixture);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(urlDaPagina(1))
      .flush({ code: 'lab-integration-pending' }, { status: 501, statusText: 'Not Implemented' });
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent;
    expect(text).toContain('Consulta de equipamentos indisponível');
    expect(text).toContain('ainda não está disponível neste ambiente');
    expect(text).not.toContain('Nenhum equipamento encontrado');
    expect(text).not.toContain('AutoLAB');
    http.verify();
  }));

  it('só pede a primeira página depois de a tabela dizer quantas linhas cabem', fakeAsync(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(EquipmentListComponent);

    http.expectNone(() => true);

    renderMeasuredTable(fixture);

    expect(http.expectOne(urlDaPagina(1)).request.params.has('search')).toBeFalse();
  }));

  /**
   * A coluna de TAG prefere a TAG de placa e cai na identificação do laudo. Imprimir só
   * `tag` deixaria a coluna mostrando o número de série nas 55% de linhas em que o cadastro
   * tem a TAG de verdade, que é o defeito que esta fatia existe para corrigir.
   */
  it('mostra a TAG de placa quando o cadastro a informa, e cai no rótulo do laudo quando não', fakeAsync(() => {
    const fixture = montarListagem();

    expect(celulas(fixture, 1)).toEqual(['TR-00001', '884120', 'LACTEC-02820/2026']);
  }));

  it('imprime o número de série, ou diz que ele não foi informado', fakeAsync(() => {
    const fixture = montarListagem();

    expect(celulas(fixture, 0)).toEqual(['571289', '884120', 'Não informado']);
  }));

  /**
   * Tipo continua anunciando ausência campo a campo, e não equipamento a equipamento: o
   * cadastro não conhece 14% das séries e deixa `TipoEquipamento` em branco em 762 linhas.
   */
  it('anuncia a ausência do tipo apenas na linha que não tem tipo', fakeAsync(() => {
    const fixture = montarListagem();

    expect(celulas(fixture, 2)).toEqual(['TRANSFORMADOR', 'Não informado', 'Não informado']);
  }));

  it('cada linha leva ao detalhe do seu equipamento', fakeAsync(() => {
    const fixture = montarListagem();
    const tela: HTMLElement = fixture.nativeElement;

    const destinos = Array.from(tela.querySelectorAll('tbody a')).map((link) =>
      link.getAttribute('href'),
    );

    expect(destinos).toEqual(LIST.map((equipment) => `/cliente/equipamentos/${equipment.id}`));
  }));

  it('pede a página seguinte ao servidor e mostra o total que o cabeçalho informa', fakeAsync(() => {
    const fixture = montarListagem(120);
    const tela: HTMLElement = fixture.nativeElement;

    expect(tela.querySelector('app-pagination')?.textContent).toContain(`1-${PAGE_SIZE} de 120`);

    clicarEmProximo(fixture);

    TestBed.inject(HttpTestingController)
      .expectOne(urlDaPagina(2))
      .flush(LIST, { headers: { 'X-Total-Count': '120' } });
    fixture.detectChanges();

    expect(tela.querySelector('app-pagination')?.textContent).toContain(
      `${PAGE_SIZE + 1}-${PAGE_SIZE * 2} de 120`,
    );
  }));

  it('envia a busca ao servidor, aparada, e mostra só o que ele devolve', fakeAsync(() => {
    const fixture = montarListagem(120);
    const http = TestBed.inject(HttpTestingController);

    digitar(fixture, '  tr-00001 ');
    esperarABusca(fixture);

    http
      .expectOne(urlDaPagina(1, 'tr-00001'))
      .flush([LIST[0]], { headers: { 'X-Total-Count': '1' } });
    fixture.detectChanges();

    expect(celulas(fixture, 0)).toEqual(['571289']);
    expect((fixture.nativeElement as HTMLElement).querySelector('app-pagination')?.textContent)
      .toContain('1-1 de 1');
    http.verify();
  }));

  it('espera a digitação parar antes de buscar, e busca uma vez só', fakeAsync(() => {
    const fixture = montarListagem();
    const http = TestBed.inject(HttpTestingController);

    digitar(fixture, '5');
    tick(SEARCH_DEBOUNCE_MS - 1);
    digitar(fixture, '57');
    tick(SEARCH_DEBOUNCE_MS - 1);
    renderMeasuredTable(fixture);

    http.expectNone(() => true);

    tick(1);
    renderMeasuredTable(fixture);

    http.expectOne(urlDaPagina(1, '57')).flush([LIST[0]], { headers: { 'X-Total-Count': '1' } });
    http.verify();
  }));

  it('volta à primeira página ao buscar', fakeAsync(() => {
    const fixture = montarListagem(120);
    const http = TestBed.inject(HttpTestingController);

    clicarEmProximo(fixture);
    http.expectOne(urlDaPagina(2)).flush(LIST, { headers: { 'X-Total-Count': '120' } });
    fixture.detectChanges();

    digitar(fixture, '884');
    esperarABusca(fixture);

    http.expectOne(urlDaPagina(1, '884')).flush([LIST[1]], { headers: { 'X-Total-Count': '1' } });
    http.verify();
  }));

  it('busca apagada volta à listagem sem filtro, e busca só de espaços não pede nada', fakeAsync(() => {
    const fixture = montarListagem();
    const http = TestBed.inject(HttpTestingController);

    digitar(fixture, '   ');
    esperarABusca(fixture);

    http.expectNone(() => true);

    digitar(fixture, '884');
    esperarABusca(fixture);
    http.expectOne(urlDaPagina(1, '884')).flush([LIST[1]], { headers: { 'X-Total-Count': '1' } });
    fixture.detectChanges();

    digitar(fixture, '');
    esperarABusca(fixture);

    const semFiltro = http.expectOne(urlDaPagina(1));
    expect(semFiltro.request.params.has('search')).toBeFalse();
    semFiltro.flush(LIST, { headers: { 'X-Total-Count': '3' } });
    http.verify();
  }));

  it('busca sem resultado orienta a revisar o termo, dentro da tabela', fakeAsync(() => {
    const fixture = montarListagem();
    const tela: HTMLElement = fixture.nativeElement;

    digitar(fixture, 'nao-existe');
    esperarABusca(fixture);
    TestBed.inject(HttpTestingController)
      .expectOne(urlDaPagina(1, 'nao-existe'))
      .flush([], { headers: { 'X-Total-Count': '0' } });
    fixture.detectChanges();

    expect(tela.querySelector('tbody')?.textContent).toContain('Nenhum equipamento encontrado');
    expect(tela.querySelector('tbody')?.textContent).toContain(
      'Revise o número de série ou a TAG informada.',
    );
    expect(tela.querySelector('app-pagination')?.textContent).toContain('0 de 0');
  }));

  it('o campo de busca não aceita mais caracteres do que a API aceita', fakeAsync(() => {
    const fixture = montarListagem();
    const campo = (fixture.nativeElement as HTMLElement).querySelector('input');

    expect(campo?.getAttribute('maxlength')).toBe('100');
  }));
});
