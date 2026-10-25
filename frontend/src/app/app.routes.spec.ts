import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { JwtHelperService } from '@auth0/angular-jwt';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';

import { UserRole } from '@core/store/user-role.store';

import { CLIENT_ROLE } from '@/app/model/roles';
import { clientToken } from '@/app/model/client-session.spec-util';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';
import { LS_TOKEN } from '@/app/services/login.service';
import { HealthService } from '@/app/services/health.service';
import { ReportsService } from '@/app/services/reports.service';

import { routes } from './app.routes';

// O segmento da rota e o input do componente casam por NOME, e nome diferente
// não é erro de compilação: é um input que nunca recebe nada. Já aconteceu, com
// `:relatorioId` na rota contra `reportId` no componente, e o efeito foi todo
// link do histórico abrir a visão geral em vez do laudo pedido. Nem o `tsc` nem
// o compilador de template enxergam isso, porque os dois lados são texto dentro
// de string: só a navegação de verdade prova.
describe('Rotas da área do cliente, ligação dos parâmetros', () => {
  const pedidos: Array<{ equipmentId: string; reportId?: string }> = [];
  const orcamentosPedidos: string[] = [];

  beforeEach(() => {
    pedidos.length = 0;
    orcamentosPedidos.length = 0;
    // A área do cliente é guardada: sem sessão o roteador desvia para o login e
    // a navegação nunca chega ao componente que está sendo medido. O papel entra
    // por substituição e não por localStorage, porque o estado inicial do store é
    // lido quando o módulo carrega, antes de qualquer preparo de teste.
    localStorage.clear();
    sessionStorage.clear();
    localStorage.setItem(LS_TOKEN, clientToken('backend-lab'));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter(routes, withComponentInputBinding()),
        { provide: JwtHelperService, useFactory: () => new JwtHelperService() },
        {
          provide: UserRole,
          useValue: { role: () => CLIENT_ROLE, dashboardPath: () => '/cliente', clear: () => {}, setRoleFromTokenClaim: () => {} },
        },
        {
          provide: ReportsService,
          useValue: {
            analysisConclusion: (quoteId: string) => {
              orcamentosPedidos.push(quoteId);
              return of(null);
            },
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
  });

  afterEach(() => {
    localStorage.clear();
    sessionStorage.clear();
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

  it('entrega à tela de relatórios o orçamento que o endereço nomeia', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/cliente/relatorios/quote-15');

    expect(orcamentosPedidos.at(-1)).toBe('quote-15');
  });
});
