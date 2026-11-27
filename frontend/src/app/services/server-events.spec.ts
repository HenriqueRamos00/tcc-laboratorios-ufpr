import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import type { EquipmentStatusRefresh, LoadFinishedNotice } from '@/app/model/equipment';

import { EquipmentsService, EquipmentStatusUnavailable } from './equipments.service';
import { ServerEventReader } from './server-events';

describe('ServerEventReader', () => {
  it('monta o evento com id, nome e dados', () => {
    const leitor = new ServerEventReader();

    expect(leitor.read('id: 7\nevent: carga.concluida\ndata: {"status":"success"}\n\n')).toEqual([
      { id: '7', name: 'carga.concluida', data: '{"status":"success"}' },
    ]);
  });

  it('guarda o pedaço que terminou no meio do evento até o resto chegar', () => {
    const leitor = new ServerEventReader();

    expect(leitor.read('event: carga.conc')).toEqual([]);
    expect(leitor.read('luida\ndata: {}\n')).toEqual([]);
    expect(leitor.read('\n')).toEqual([{ id: null, name: 'carga.concluida', data: '{}' }]);
  });

  it('ignora comentário e bloco sem evento nem dado', () => {
    const leitor = new ServerEventReader();

    expect(leitor.read(': ping\n\nid: 3\n\nevent: ping\ndata:\n\n')).toEqual([
      { id: null, name: 'ping', data: '' },
    ]);
  });

  it('aceita quebra de linha do Windows e evento sem nome', () => {
    const leitor = new ServerEventReader();

    expect(leitor.read('data: a\r\ndata: b\r\n\r\n')).toEqual([{ id: null, name: 'message', data: 'a\nb' }]);
  });
});

describe('EquipmentsService: situação pelos laudos', () => {
  let service: EquipmentsService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EquipmentsService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('manda o filtro de situação junto com a página e a busca', () => {
    service.list({ page: 2, pageSize: 10, search: ' tr ', status: 'nao-conforme' }).subscribe();

    const pedido = http.expectOne(
      `${API_URL}/api/equipments?page=2&pageSize=10&search=tr&status=nao-conforme`,
    );
    pedido.flush([]);

    expect(pedido.request.method).toBe('GET');
  });

  it('pede a atualização de um equipamento por POST, com o identificador opaco', () => {
    let recebido: EquipmentStatusRefresh | undefined;

    service.refreshStatus('eq/1').subscribe((corpo) => (recebido = corpo));
    const pedido = http.expectOne(`${API_URL}/api/equipments/eq%2F1/status/refresh`);
    pedido.flush({ alreadyRunning: true, success: true, reportsRead: 0, reportsUnreadable: 0, status: null });

    expect(pedido.request.method).toBe('POST');
    expect(recebido?.alreadyRunning).toBeTrue();
  });

  it('distingue o ambiente sem a projeção de um equipamento que não existe', () => {
    const erros: unknown[] = [];

    service.refreshStatus('a').subscribe({ error: (erro: unknown) => erros.push(erro) });
    http.expectOne(`${API_URL}/api/equipments/a/status/refresh`)
      .flush({ code: 'equipment-status-unavailable' }, { status: 404, statusText: 'Not Found' });
    service.refreshStatus('b').subscribe({ error: (erro: unknown) => erros.push(erro) });
    http.expectOne(`${API_URL}/api/equipments/b/status/refresh`)
      .flush({ code: 'equipment-without-analysis' }, { status: 404, statusText: 'Not Found' });

    expect(erros[0]).toBeInstanceOf(EquipmentStatusUnavailable);
    expect(erros[1]).not.toBeInstanceOf(EquipmentStatusUnavailable);
  });

  it('lê a situação de um equipamento, e nulo quando ele ainda não está na projeção', () => {
    const recebidos: unknown[] = [];

    service.status('eq').subscribe((corpo) => recebidos.push(corpo));
    http.expectOne(`${API_URL}/api/equipments/eq/status`).flush(null, { status: 204, statusText: 'No Content' });

    expect(recebidos).toEqual([null]);
  });

  it('entrega só os avisos de fim de carga do canal de eventos', () => {
    const avisos: LoadFinishedNotice[] = [];

    service.loadFinishedNotices().subscribe((aviso) => avisos.push(aviso));
    const pedido = http.expectOne(`${API_URL}/api/events`);
    pedido.flush(
      'event: ping\ndata:\n\n'
        + 'id: 4\nevent: carga.concluida\ndata: {"status":"success","at":"2026-11-10T09:00:00Z","reportsRead":3}\n\n',
    );

    expect(pedido.request.headers.get('Accept')).toBe('text/event-stream');
    expect(avisos).toEqual([{ status: 'success', at: '2026-11-10T09:00:00Z', reportsRead: 3 }]);
  });
});
