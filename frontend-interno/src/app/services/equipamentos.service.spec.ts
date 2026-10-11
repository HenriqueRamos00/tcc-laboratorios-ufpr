import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Equipamento, EquipamentoRequest } from '@/app/model/equipamento';
import { EquipamentosService } from './equipamentos.service';

describe('Serviço de equipamentos', () => {
  let service: EquipamentosService;
  let http: HttpTestingController;

  const request: EquipamentoRequest = {
    serialNumber: '000123',
    tag: 'TR-B05',
    substation: 'Subestação Norte',
    primaryVoltage: '138',
    owner: 'Copel',
    type: 'TRANSFORMADOR',
    riskLevel: 'ATENCAO',
  };
  const equipamento: Equipamento = { ...request, id: 12 };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(EquipamentosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista todos os equipamentos sem filtros', () => {
    service.list('', null, null).subscribe();
    const req = http.expectOne('/api/equipments');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.keys()).toEqual([]);
    req.flush([]);
  });

  it('normaliza a busca e combina tipo e risco', () => {
    service.list('  TR-B05  ', 'TRANSFORMADOR', 'ATENCAO').subscribe();
    const req = http.expectOne((r) => r.url === '/api/equipments' &&
      r.params.get('search') === 'TR-B05' && r.params.get('type') === 'TRANSFORMADOR' && r.params.get('risk') === 'ATENCAO');
    expect(req.request.method).toBe('GET');
    req.flush([equipamento]);
  });

  for (const [filter, value] of [['type', 'DISJUNTOR'], ['risk', 'CRITICO']] as const) {
    it(`envia o filtro ${filter} e omite o filtro nulo`, () => {
      service.list('', filter === 'type' ? value as 'DISJUNTOR' : null, filter === 'risk' ? value as 'CRITICO' : null).subscribe();
      const req = http.expectOne('/api/equipments?' + filter + '=' + value);
      expect(req.request.params.keys()).toEqual([filter]);
      req.flush([]);
    });
  }

  it('cria equipamento preservando tensão e número de série como strings', () => {
    service.create(request).subscribe();
    const req = http.expectOne('/api/equipments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    expect(typeof req.request.body.primaryVoltage).toBe('string');
    expect(req.request.body.serialNumber).toBe('000123');
    req.flush(equipamento);
  });

  it('atualiza equipamento', () => {
    service.update(12, request).subscribe();
    const req = http.expectOne('/api/equipments/12');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(equipamento);
  });

  it('desativa equipamento', () => {
    service.deactivate(12).subscribe();
    const req = http.expectOne('/api/equipments/12');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('propaga falhas da API', () => {
    let status = 0;
    service.list('', null, null).subscribe({ error: (error) => status = error.status });
    http.expectOne('/api/equipments').flush({ message: 'Falha' }, { status: 500, statusText: 'Server Error' });
    expect(status).toBe(500);
  });
});
