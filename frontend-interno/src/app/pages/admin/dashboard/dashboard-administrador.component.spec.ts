import { registerLocaleData } from '@angular/common';
import ptBr from '@angular/common/locales/pt';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { provideRouter } from '@angular/router';

import { UserRole } from '@core/store/user-role.store';
import { DashboardAdministrador } from '@/app/model/dashboard-administrador';
import { DashboardAdministradorComponent } from './dashboard-administrador.component';

registerLocaleData(ptBr);

describe('Dashboard do administrador', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DashboardAdministradorComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardAdministradorComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt' },
        {
          provide: UserRole,
          useValue: { user: () => ({ name: 'Marina Almeida' }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardAdministradorComponent);
  });

  afterEach(() => http.verify());

  it('mostra carregamento e atalhos enquanto aguarda os indicadores', () => {
    fixture.detectChanges();
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');

    expect(texto).toContain('Carregando indicadores do painel.');
    expect(texto).toContain('Olá, Marina Almeida');
    expect(texto).toContain('Orçamentos');
    expect(texto).toContain('Técnicos');
    expect(texto).toContain('Empresas');
    expect(texto).toContain('Clientes');
    expect(texto).not.toContain('R$ 0,00');
    http.expectOne('/api/admin/dashboard').flush({
      totalQuotes: 0,
      pendingAcceptance: 0,
      activeClients: 0,
      totalQuotedValue: 0,
    });
  });

  it('exibe indicadores recebidos em formato brasileiro', () => {
    fixture.detectChanges();
    const resposta: DashboardAdministrador = {
      totalQuotes: 1234,
      pendingAcceptance: 18,
      activeClients: 42,
      totalQuotedValue: 1501.5,
    };
    http.expectOne('/api/admin/dashboard').flush(resposta);
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('Total de Orçamentos');
    expect(texto).toContain('1.234');
    expect(texto).toContain('Pendentes de Aceite');
    expect(texto).toContain('Clientes Ativos');
    expect(texto).toContain('Valor Total Orçado');
    expect(texto).toContain('R$ 1.501,50');
  });

  it('preserva o valor extenso completo com os centavos no último grupo', () => {
    fixture.detectChanges();
    http.expectOne('/api/admin/dashboard').flush({
      totalQuotes: 1234,
      pendingAcceptance: 18,
      activeClients: 42,
      totalQuotedValue: 1234567890123.45,
    });
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('R$ 1.234.567.890.123,45');
  });

  it('exibe zeros quando a resposta válida não contém registros', () => {
    fixture.detectChanges();
    http.expectOne('/api/admin/dashboard').flush({
      totalQuotes: 0,
      pendingAcceptance: 0,
      activeClients: 0,
      totalQuotedValue: 0,
    });
    fixture.detectChanges();

    const paragrafos = fixture.nativeElement.querySelectorAll(
      'section[aria-label="Indicadores do dashboard"] article p',
    ) as NodeListOf<HTMLElement>;
    const valores = Array.from(paragrafos).map((paragrafo) =>
      paragrafo.textContent?.trim().replace(/\s+/g, ' '),
    );

    expect(valores).toEqual(['0', '0', '0', 'R$ 0,00']);
  });

  it('permite repetir a consulta depois de uma falha', () => {
    fixture.detectChanges();
    http.expectOne('/api/admin/dashboard').flush(
      { message: 'Falha na consulta' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Não foi possível carregar os indicadores');
    expect(texto).toContain('Orçamentos');

    const botao = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    botao.click();
    fixture.detectChanges();
    const retry = http.expectOne('/api/admin/dashboard');
    expect(retry.request.method).toBe('GET');
    retry.flush({
      totalQuotes: 0,
      pendingAcceptance: 0,
      activeClients: 0,
      totalQuotedValue: 0,
    });
    fixture.detectChanges();

    expect((fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ')).toContain('R$ 0,00');
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar os indicadores');
  });
});
