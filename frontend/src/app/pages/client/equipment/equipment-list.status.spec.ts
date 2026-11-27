import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { type ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import {
  PAGE_SIZE_WITHOUT_SCROLL_AREA,
  renderMeasuredTable,
} from '@shared/components/data-table/data-table.spec-util';

import { API_URL } from '@/app/environment/env';
import type { Equipment, EquipmentStatus } from '@/app/model/equipment';

import {
  EquipmentListComponent,
  NOTICE_WAIT_LIMIT_MS,
  SYNC_ALL_WAIT_LIMIT_MS,
} from './equipment-list.component';

const LIST_URL = `${API_URL}/api/equipments`;
const EVENTS_URL = `${API_URL}/api/events`;
const BATCH_URL = `${LIST_URL}/status/refresh`;
const SYNC_URL = `${LIST_URL}/status/sync`;
const FIRST_PAGE = `${LIST_URL}?page=1&pageSize=${PAGE_SIZE_WITHOUT_SCROLL_AREA}`;
const STATUS_COLUMN = 4;

const NAO_CONFORME: EquipmentStatus = {
  overall: 'nao-conforme',
  chromatography: { conformity: 'nao-conforme', resultLabel: 'Descarga elétrica' },
  physicalChemical: null,
  lastReportDate: '2026-09-24',
  updatedAt: '2026-11-10T09:00:00Z',
};

const EM_ANDAMENTO = {
  alreadyRunning: true,
  success: true,
  reportsRead: 0,
  reportsUnreadable: 0,
  status: null,
};

function equipamento(id: string, serialNumber: string, status?: EquipmentStatus | null): Equipment {
  return {
    id,
    serialNumber,
    tag: serialNumber,
    equipmentTag: null,
    type: 'TRANSFORMADOR',
    location: null,
    company: 'EMPRESA DE ENERGIA',
    identification: 'LACTEC-02272/2026',
    reportCount: 1,
    mostRecentReportDate: '2026-06-23',
    nameplateKnown: true,
    ...(status === undefined ? {} : { status }),
  };
}

const LIST: Equipment[] = [
  equipamento('QQ', '571289'),
  equipamento('Qg', '884120'),
  equipamento('Qw', '990001'),
];

type Tela = ComponentFixture<EquipmentListComponent>;

function montar(rows: Equipment[] = LIST, total = rows.length): Tela {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });

  const fixture = TestBed.createComponent(EquipmentListComponent);
  renderMeasuredTable(fixture);
  TestBed.inject(HttpTestingController)
    .expectOne(FIRST_PAGE)
    .flush(rows, { headers: { 'X-Total-Count': String(total) } });
  fixture.detectChanges();
  return fixture;
}

function situacoes(fixture: Tela): string[] {
  const tela: HTMLElement = fixture.nativeElement;
  return Array.from(tela.querySelectorAll('tbody tr[data-row]')).map((linha) => {
    const celula = linha.querySelectorAll('td')[STATUS_COLUMN].cloneNode(true) as HTMLElement;
    celula.querySelectorAll('mat-icon, .sr-only').forEach((oculto) => oculto.remove());
    return celula.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  });
}

function botao(fixture: Tela, linha: number): HTMLButtonElement | null {
  const tela: HTMLElement = fixture.nativeElement;
  return tela.querySelectorAll('tbody tr[data-row]')[linha].querySelector('app-equipment-status button');
}

function clicar(fixture: Tela, linha: number): void {
  botao(fixture, linha)?.click();
  fixture.detectChanges();
}

function atualizacaoDe(equipment: Equipment): string {
  return `${LIST_URL}/${equipment.id}/status/refresh`;
}

function situacaoDe(equipment: Equipment): string {
  return `${LIST_URL}/${equipment.id}/status`;
}

function escolherSituacao(fixture: Tela, valor: string): void {
  const filtro = (fixture.nativeElement as HTMLElement).querySelector(
    '[data-tour="equipamentos-situacao"] select',
  ) as HTMLSelectElement;
  filtro.value = valor;
  filtro.dispatchEvent(new Event('change'));
  renderMeasuredTable(fixture);
}

describe('EquipmentListComponent: situação pelos laudos', () => {
  it('mostra o rótulo onde há dado e o botão Atualizar onde não há', fakeAsync(() => {
    const fixture = montar([equipamento('QQ', '571289', NAO_CONFORME), LIST[1], LIST[2]]);

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizar', 'Atualizar']);
    expect(botao(fixture, 0)).toBeNull();
    expect(botao(fixture, 1)).not.toBeNull();
  }));

  it('clicar em Atualizar pede a leitura daquele equipamento e troca o botão pelo rótulo', fakeAsync(() => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);

    clicar(fixture, 1);

    const pedido = http.expectOne(atualizacaoDe(LIST[1]));
    expect(pedido.request.method).toBe('POST');
    expect(situacoes(fixture)).toEqual(['Atualizar', 'Atualizando', 'Atualizar']);
    expect(botao(fixture, 1)?.disabled).toBeTrue();

    pedido.flush({ ...EM_ANDAMENTO, alreadyRunning: false, reportsRead: 2, status: NAO_CONFORME });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Atualizar', 'Não conforme', 'Atualizar']);
    http.verify();
  }));

  it('um segundo clique na mesma linha não abre outro pedido', fakeAsync(() => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);

    clicar(fixture, 0);
    clicar(fixture, 0);

    expect(http.match(atualizacaoDe(LIST[0])).length).toBe(1);
  }));

  it('com outra rodada em andamento, espera o aviso de conclusão e então busca a situação', fakeAsync(() => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);

    clicar(fixture, 0);
    http.expectOne(atualizacaoDe(LIST[0])).flush(EM_ANDAMENTO);
    fixture.detectChanges();

    expect(situacoes(fixture)[0]).toBe('Atualizando');
    http.expectNone(situacaoDe(LIST[0]));

    http.expectOne(EVENTS_URL).flush(
      'event: ping\ndata:\n\n'
        + 'id: 9\nevent: carga.concluida\ndata: {"status":"success","at":"2026-11-10T09:00:00Z","reportsRead":5}\n\n',
    );
    http.expectOne(situacaoDe(LIST[0])).flush(NAO_CONFORME);
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizar', 'Atualizar']);
    http.verify();
  }));

  it('duas linhas esperando a mesma rodada dividem uma conexão de avisos', fakeAsync(() => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);

    clicar(fixture, 0);
    http.expectOne(atualizacaoDe(LIST[0])).flush(EM_ANDAMENTO);
    clicar(fixture, 1);
    http.expectOne(atualizacaoDe(LIST[1])).flush(EM_ANDAMENTO);

    http.expectOne(EVENTS_URL).flush(
      'event: carga.concluida\ndata: {"status":"failure","at":"2026-11-10T09:00:00Z","reportsRead":1}\n\n',
    );
    http.expectOne(situacaoDe(LIST[0])).flush(NAO_CONFORME);
    http.expectOne(situacaoDe(LIST[1])).flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(situacoes(fixture)[0]).toBe('Não conforme');
    expect(situacoes(fixture)[1]).toBe('Tentar de novo');
    http.verify();
  }));

  it('se o aviso não chega até o teto, pergunta a situação mesmo assim', fakeAsync(() => {
    const fixture = montar();
    const http = TestBed.inject(HttpTestingController);

    clicar(fixture, 0);
    http.expectOne(atualizacaoDe(LIST[0])).flush(EM_ANDAMENTO);
    const avisos = http.expectOne(EVENTS_URL);

    tick(NOTICE_WAIT_LIMIT_MS);

    expect(avisos.cancelled).toBeTrue();
    http.expectOne(situacaoDe(LIST[0])).flush(NAO_CONFORME);
    fixture.detectChanges();

    expect(situacoes(fixture)[0]).toBe('Não conforme');
  }));

  it('falha na atualização devolve o botão e diz que não deu', fakeAsync(() => {
    const fixture = montar();

    clicar(fixture, 2);
    TestBed.inject(HttpTestingController)
      .expectOne(atualizacaoDe(LIST[2]))
      .flush({ code: 'lab-test-source-unavailable' }, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();

    expect(botao(fixture, 2)?.disabled).toBeFalse();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('app-equipment-status [role="alert"]')?.textContent,
    ).toContain('Não foi possível atualizar a situação de 990001');
    expect(situacoes(fixture)[2]).toBe('Tentar de novo');
    expect(situacoes(fixture)[0]).toBe('Atualizar');
  }));

  it('onde o ambiente não tem a projeção dos laudos, recolhe o botão de todas as linhas', fakeAsync(() => {
    const fixture = montar();

    clicar(fixture, 0);
    TestBed.inject(HttpTestingController)
      .expectOne(atualizacaoDe(LIST[0]))
      .flush({ code: 'equipment-status-unavailable' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-equipment-status button')).toBeNull();
    expect(situacoes(fixture)[1]).toContain('Dados não disponíveis');
  }));

  it('o filtro de situação vai ao servidor na primeira página e some ao escolher Todas', fakeAsync(() => {
    const fixture = montar(LIST, 120);
    const http = TestBed.inject(HttpTestingController);

    escolherSituacao(fixture, 'nao-conforme');

    http
      .expectOne(`${FIRST_PAGE}&status=nao-conforme`)
      .flush([equipamento('QQ', '571289', NAO_CONFORME)], { headers: { 'X-Total-Count': '1' } });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme']);

    escolherSituacao(fixture, '');

    const semFiltro = http.expectOne(FIRST_PAGE);
    expect(semFiltro.request.params.has('status')).toBeFalse();
    semFiltro.flush(LIST, { headers: { 'X-Total-Count': '3' } });
    http.verify();
  }));

  it('oferece as situações na ordem de gravidade, e filtro sem resultado explica o porquê', fakeAsync(() => {
    const fixture = montar();
    const tela: HTMLElement = fixture.nativeElement;
    const opcoes = Array.from(
      tela.querySelectorAll('[data-tour="equipamentos-situacao"] option'),
    ).map((opcao) => opcao.textContent?.trim());

    expect(opcoes).toEqual(['Todas', 'Não conforme', 'Alerta', 'Normal', 'Sem laudo', 'Sem classificação']);

    escolherSituacao(fixture, 'sem-laudo');
    TestBed.inject(HttpTestingController)
      .expectOne(`${FIRST_PAGE}&status=sem-laudo`)
      .flush([], { headers: { 'X-Total-Count': '0' } });
    fixture.detectChanges();

    expect(tela.querySelector('tbody')?.textContent)
      .toContain('Só entram neste filtro os equipamentos que já tiveram os laudos lidos');
  }));
});

const NORMAL: EquipmentStatus = { ...NAO_CONFORME, overall: 'normal', chromatography: null };

const SEM_PROJECAO: Equipment[] = [
  equipamento('QQ', '571289', NAO_CONFORME),
  equipamento('Qg', '884120', null),
  equipamento('Qw', '990001', null),
];

const AVISO = 'event: carga.concluida\ndata: {"status":"success","at":"2026-11-10T09:00:00Z","reportsRead":2}\n\n';

function botaoDoTitulo(fixture: Tela): HTMLButtonElement | null {
  return (fixture.nativeElement as HTMLElement).querySelector(
    'app-page-header [data-tour="equipamentos-atualizar"]',
  );
}

function textoDoBotaoDoTitulo(fixture: Tela): string {
  const copia = botaoDoTitulo(fixture)?.cloneNode(true) as HTMLElement;
  copia.querySelector('mat-icon')?.remove();
  return copia.textContent?.trim() ?? '';
}

function irParaAProximaPagina(fixture: Tela): void {
  const proximo = Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('app-pagination button'),
  ).find((opcao) => opcao.textContent?.includes('Próximo')) as HTMLButtonElement;
  proximo.click();
  renderMeasuredTable(fixture);
}

describe('EquipmentListComponent: sincronização automática da página exibida', () => {
  it('pede num único lote os equipamentos visíveis que ainda não estão na projeção', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    const http = TestBed.inject(HttpTestingController);

    const lote = http.expectOne(BATCH_URL);
    expect(lote.request.method).toBe('POST');
    expect(lote.request.body).toEqual({ ids: ['Qg', 'Qw'] });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizando', 'Atualizando']);
    expect(botao(fixture, 1)?.disabled).toBeTrue();
    expect(botao(fixture, 1)?.getAttribute('aria-busy')).toBe('true');

    lote.flush({ accepted: 2, alreadyRunning: false }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizando', 'Atualizando']);
    http.expectNone(FIRST_PAGE);

    http.expectOne(EVENTS_URL).flush(AVISO);
    http
      .expectOne(FIRST_PAGE)
      .flush([SEM_PROJECAO[0], equipamento('Qg', '884120', NORMAL), SEM_PROJECAO[2]], {
        headers: { 'X-Total-Count': '3' },
      });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Normal', 'Atualizar']);
    expect(botao(fixture, 2)?.disabled).toBeFalse();
    http.verify();
    fixture.destroy();
  }));

  it('linha sem o campo de situação não entra no lote, e lista toda lida não pede nada', fakeAsync(() => {
    const fixture = montar([SEM_PROJECAO[0], equipamento('Qg', '884120'), equipamento('Qw', '990001', NORMAL)]);

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizar', 'Normal']);
    TestBed.inject(HttpTestingController).verify();
  }));

  it('mudar de filtro e voltar não pede de novo as chaves que já foram pedidas', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne(BATCH_URL).flush({ accepted: 2, alreadyRunning: false });
    escolherSituacao(fixture, 'normal');
    http.expectOne(`${FIRST_PAGE}&status=normal`).flush([], { headers: { 'X-Total-Count': '0' } });
    escolherSituacao(fixture, '');
    http.expectOne(FIRST_PAGE).flush(SEM_PROJECAO, { headers: { 'X-Total-Count': '3' } });
    fixture.detectChanges();

    http.expectNone(BATCH_URL);
    expect(http.match(EVENTS_URL).length).toBe(1);
    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizando', 'Atualizando']);
    fixture.destroy();
  }));

  it('mudar de página pede só as chaves novas', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO, 120);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne(BATCH_URL).flush({ accepted: 2, alreadyRunning: false });
    irParaAProximaPagina(fixture);
    http
      .expectOne(`${LIST_URL}?page=2&pageSize=${PAGE_SIZE_WITHOUT_SCROLL_AREA}`)
      .flush([SEM_PROJECAO[2], equipamento('RA', '111111', null)], { headers: { 'X-Total-Count': '120' } });
    fixture.detectChanges();

    expect(http.expectOne(BATCH_URL).request.body).toEqual({ ids: ['RA'] });
    expect(situacoes(fixture)).toEqual(['Atualizando', 'Atualizando']);
    expect(http.match(EVENTS_URL).length).toBe(1);
    fixture.destroy();
  }));

  it('quando o lote já estava todo na projeção, recarrega a página sem esperar aviso', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne(BATCH_URL).flush({ accepted: 0, alreadyRunning: false });
    http
      .expectOne(FIRST_PAGE)
      .flush(
        [SEM_PROJECAO[0], equipamento('Qg', '884120', NORMAL), equipamento('Qw', '990001', NORMAL)],
        { headers: { 'X-Total-Count': '3' } },
      );
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Normal', 'Normal']);
    http.expectNone(BATCH_URL);
    fixture.destroy();
  }));

  it('se o aviso não chega até o teto, recarrega a página mesmo assim', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne(BATCH_URL).flush({ accepted: 2, alreadyRunning: true });
    tick(NOTICE_WAIT_LIMIT_MS);
    http.expectOne(FIRST_PAGE).flush(SEM_PROJECAO, { headers: { 'X-Total-Count': '3' } });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizar', 'Atualizar']);
    http.expectNone(BATCH_URL);
    fixture.destroy();
  }));

  it('onde o ambiente não tem a projeção, recolhe os botões das linhas e o do título', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);

    TestBed.inject(HttpTestingController)
      .expectOne(BATCH_URL)
      .flush({ code: 'equipment-status-unavailable' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-equipment-status button')).toBeNull();
    expect(situacoes(fixture)[1]).toContain('Dados não disponíveis');
    expect(botaoDoTitulo(fixture)).toBeNull();
    fixture.destroy();
  }));

  it('falha no lote devolve o botão das linhas, sem pedir de novo sozinho', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne(BATCH_URL).flush({ code: 'falha' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(situacoes(fixture)).toEqual(['Não conforme', 'Atualizar', 'Atualizar']);
    expect(botao(fixture, 1)?.disabled).toBeFalse();
    http.expectNone(BATCH_URL);
    fixture.destroy();
  }));
});

describe('EquipmentListComponent: botão Atualizar do título', () => {
  it('fica na linha do título, com o estilo do botão primário e o ícone à direita', fakeAsync(() => {
    const fixture = montar();
    const titulo = botaoDoTitulo(fixture) as HTMLButtonElement;

    expect(titulo.closest('[acoes]')?.parentElement?.querySelector('h1')?.textContent)
      .toContain('Listagem de Equipamentos');
    expect(titulo.closest('[acoes]')?.parentElement?.classList).toContain('justify-between');
    for (const classe of ['bg-lactec-primary', 'text-lactec-on-primary', 'rounded-lg', 'font-semibold']) {
      expect(titulo.classList).toContain(classe);
    }
    expect(textoDoBotaoDoTitulo(fixture)).toBe('Atualizar');
    expect(titulo.lastElementChild?.tagName.toLowerCase()).toBe('mat-icon');
    expect(titulo.lastElementChild?.textContent?.trim()).toBe('sync');
    expect(titulo.disabled).toBeFalse();
  }));

  it('pede os demais equipamentos do cliente, mostra Atualizando e recarrega a lista no aviso', fakeAsync(() => {
    const fixture = montar([equipamento('QQ', '571289', NAO_CONFORME)]);
    const http = TestBed.inject(HttpTestingController);

    botaoDoTitulo(fixture)?.click();
    botaoDoTitulo(fixture)?.click();
    fixture.detectChanges();

    const pedido = http.expectOne(SYNC_URL);
    expect(pedido.request.method).toBe('POST');
    expect(textoDoBotaoDoTitulo(fixture)).toBe('Atualizando…');
    expect(botaoDoTitulo(fixture)?.disabled).toBeTrue();
    expect(botaoDoTitulo(fixture)?.getAttribute('aria-busy')).toBe('true');
    expect(botaoDoTitulo(fixture)?.querySelector('mat-icon')?.classList).toContain('animate-spin');

    pedido.flush({ accepted: 1, alreadyRunning: true }, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(textoDoBotaoDoTitulo(fixture)).toBe('Atualizando…');

    http.expectOne(EVENTS_URL).flush(AVISO);
    http
      .expectOne(FIRST_PAGE)
      .flush([equipamento('QQ', '571289', NAO_CONFORME), equipamento('Qg', '884120', NORMAL)], {
        headers: { 'X-Total-Count': '2' },
      });
    fixture.detectChanges();

    expect(textoDoBotaoDoTitulo(fixture)).toBe('Atualizar');
    expect(botaoDoTitulo(fixture)?.disabled).toBeFalse();
    expect(situacoes(fixture)).toEqual(['Não conforme', 'Normal']);
    http.verify();
  }));

  it('depois da rodada do cliente, a página volta a pedir o que continuou sem situação', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    const http = TestBed.inject(HttpTestingController);

    http.expectOne(BATCH_URL).flush({ accepted: 2, alreadyRunning: false });
    botaoDoTitulo(fixture)?.click();
    http.expectOne(SYNC_URL).flush({ accepted: 1, alreadyRunning: true });
    http.expectOne(EVENTS_URL).flush(AVISO);
    http.expectOne(FIRST_PAGE).flush(SEM_PROJECAO, { headers: { 'X-Total-Count': '3' } });
    fixture.detectChanges();

    expect(http.expectOne(BATCH_URL).request.body).toEqual({ ids: ['Qg', 'Qw'] });
    fixture.destroy();
  }));

  it('se o aviso não chega até o teto, devolve o botão e recarrega a lista', fakeAsync(() => {
    const fixture = montar([equipamento('QQ', '571289', NAO_CONFORME)]);
    const http = TestBed.inject(HttpTestingController);

    botaoDoTitulo(fixture)?.click();
    http.expectOne(SYNC_URL).flush({ accepted: 1, alreadyRunning: false });
    tick(SYNC_ALL_WAIT_LIMIT_MS);
    fixture.detectChanges();

    expect(textoDoBotaoDoTitulo(fixture)).toBe('Atualizar');
    http.expectOne(FIRST_PAGE).flush([], { headers: { 'X-Total-Count': '0' } });
    fixture.destroy();
  }));

  it('falha no pedido devolve o botão e diz que não deu', fakeAsync(() => {
    const fixture = montar([equipamento('QQ', '571289', NAO_CONFORME)]);
    const http = TestBed.inject(HttpTestingController);

    botaoDoTitulo(fixture)?.click();
    http.expectOne(SYNC_URL).flush({ code: 'falha' }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(textoDoBotaoDoTitulo(fixture)).toBe('Atualizar');
    expect(botaoDoTitulo(fixture)?.disabled).toBeFalse();
    expect((fixture.nativeElement as HTMLElement).querySelector('app-page-header [role="alert"]')?.textContent)
      .toContain('Não foi possível atualizar');
    fixture.destroy();
  }));

  it('a célula da situação reserva a mesma largura para rótulo, botão e atualizando', fakeAsync(() => {
    const fixture = montar(SEM_PROJECAO);
    fixture.detectChanges();
    const celulas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr[data-row] app-equipment-status'),
    );

    expect(celulas.length).toBe(3);
    for (const celula of celulas) {
      expect(celula.classList).toContain('w-40');
      expect(celula.classList).toContain('h-6');
      expect(celula.querySelector('app-status-pill > span, button')?.classList).toContain('w-40');
      expect(celula.querySelector('app-status-pill > span, button')?.classList).toContain('h-6');
    }
    fixture.destroy();
  }));
});
