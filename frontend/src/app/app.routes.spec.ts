import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { UserRole } from '@core/store/user-role.store';

import { CLIENT_ROLE } from '@/app/model/roles';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';
import { LoginService, LS_SESSION } from '@/app/services/login.service';
import { HealthService } from '@/app/services/health.service';

import { routes } from './app.routes';

// O segmento da rota e o input do componente casam por NOME, e nome diferente
// não é erro de compilação: é um input que nunca recebe nada. Já aconteceu, com
// `:relatorioId` na rota contra `reportId` no componente, e o efeito foi todo
// link do histórico abrir a visão geral em vez do laudo pedido. Nem o `tsc` nem
// o compilador de template enxergam isso, porque os dois lados são texto dentro
// de string: só a navegação de verdade prova.
describe('Rotas da área do cliente, ligação dos parâmetros', () => {
  const pedidos: Array<{ equipmentId: string; reportId?: string }> = [];

  beforeEach(() => {
    pedidos.length = 0;
    localStorage.removeItem(LS_SESSION);
    sessionStorage.removeItem(LS_SESSION);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter(routes, withComponentInputBinding()),
        {
          provide: UserRole,
          useValue: {
            role: () => CLIENT_ROLE, dashboardPath: () => '/cliente',
            setRole: () => {}, clear: () => {},
          },
        },
        {
          provide: HealthService,
          useValue: {
            equipmentIndicators: (equipmentId: string, reportId?: string) => {
              pedidos.push({ equipmentId, reportId });
              return of(HEALTH_INDICATORS);
            },
          },
        },
      ],
    });
    TestBed.inject(LoginService).login({ email: 'lactec@lactec', password: 'lactec' }).subscribe();
  });

  afterEach(() => {
    localStorage.removeItem(LS_SESSION);
    sessionStorage.removeItem(LS_SESSION);
  });

  it('entrega ao componente o laudo que o endereço nomeia', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/cliente/equipamentos/MTExLjU5NC8x/indicadores/MDAxODgvMjAyNi0wMDQ');

    expect(pedidos.at(-1)).toEqual({
      equipmentId: 'MTExLjU5NC8x',
      reportId: 'MDAxODgvMjAyNi0wMDQ',
    });
  });

  it('pede a visão geral quando o endereço não nomeia laudo nenhum', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/cliente/equipamentos/MTExLjU5NC8x/indicadores');

    expect(pedidos.at(-1)).toEqual({ equipmentId: 'MTExLjU5NC8x', reportId: undefined });
  });
});
