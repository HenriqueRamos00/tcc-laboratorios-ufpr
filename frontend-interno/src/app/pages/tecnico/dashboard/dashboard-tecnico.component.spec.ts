import { registerLocaleData } from '@angular/common';
import ptBr from '@angular/common/locales/pt';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { provideRouter } from '@angular/router';

import { UserRole } from '@core/store/user-role.store';
import { DashboardTecnico, DashboardTecnicoReport } from '@/app/model/dashboard-tecnico';
import { DashboardTecnicoComponent } from './dashboard-tecnico.component';

registerLocaleData(ptBr);

describe('Dashboard do técnico', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<DashboardTecnicoComponent>;

  const laudos = (quantidade: number): DashboardTecnicoReport[] =>
    Array.from({ length: quantidade }, (_, indice) => ({
      id: String(31 - indice),
      companyName: `Empresa ${indice + 1}`,
      type: 'Cromatografia de gases dissolvidos',
      validationStatus: 'pending-validation' as const,
    }));

  const resposta = (
    ajustes: Partial<DashboardTecnico> = {},
  ): DashboardTecnico => ({
    testsInExecution: 3,
    testsAwaitingApproval: 2,
    testsFinished: 5,
    pendingReportsTotal: 0,
    pendingReports: [],
    ...ajustes,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardTecnicoComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'pt' },
        {
          provide: UserRole,
          useValue: { user: () => ({ name: 'Angelo' }) },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(DashboardTecnicoComponent);
  });

  afterEach(() => http.verify());

  it('mostra carregamento, identificação técnica e atalhos enquanto aguarda a API', () => {
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;

    expect(texto).toContain('Olá, Angelo');
    expect(texto).toContain('Dashboard de técnico');
    expect(texto).toContain('Painel técnico');
    expect(texto).toContain('Carregando indicadores do painel técnico.');
    expect(texto).toContain('Manter ensaio');
    expect(texto).toContain('Novo ensaio');
    expect(texto).toContain('Pesquisar relatório');
    expect(texto).toContain('Equipamentos');
    expect(texto).not.toContain('Amostras aguardando');
    expect(texto).not.toContain('0');
    const atalhos = fixture.nativeElement.querySelectorAll(
      'nav[aria-label="Acesso rápido aos módulos técnicos"] a',
    ) as NodeListOf<HTMLAnchorElement>;
    expect(Array.from(atalhos, (atalho) => atalho.getAttribute('href'))).toEqual([
      '/tecnico/ensaios',
      '/tecnico/ensaios/novo',
      '/tecnico/relatorios',
      '/tecnico/equipamentos',
    ]);

    const request = http.expectOne('/api/technician/dashboard');
    expect(request.request.method).toBe('GET');
    request.flush(resposta());
  });

  it('exibe os três indicadores e a prévia de até seis laudos com o total informado', () => {
    fixture.detectChanges();
    const request = http.expectOne('/api/technician/dashboard');
    request.flush(resposta({ pendingReportsTotal: 8, pendingReports: laudos(6) }));
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    const metricas = fixture.nativeElement.querySelectorAll(
      'section[aria-label="Indicadores do dashboard"] article p',
    ) as NodeListOf<HTMLElement>;
    const valores = Array.from(metricas, (elemento) => elemento.textContent?.trim());
    const linksDeLaudos = fixture.nativeElement.querySelectorAll(
      'section[aria-labelledby="laudos-pendentes-titulo"] tbody a',
    ) as NodeListOf<HTMLAnchorElement>;

    expect(valores).toEqual(['3', '2', '5']);
    expect(texto).toContain('Ensaios em execução');
    expect(texto).toContain('Ensaios pendentes de aprovação');
    expect(texto).toContain('Ensaios finalizados');
    expect(texto).toContain('Exibindo 6 de 8 laudos pendentes de validação.');
    expect(texto).toContain('Empresa 1');
    expect(texto).toContain('Cromatografia de gases dissolvidos');
    expect(texto).toContain('Aguardando validação');
    expect(linksDeLaudos.length).toBe(6);
    expect(linksDeLaudos[0].textContent?.trim()).toBe('31');
    expect(linksDeLaudos[0].getAttribute('href')).toBe('/tecnico/relatorios/31');
    expect(fixture.nativeElement.querySelector('a[aria-label="Ver todos os relatórios"]')?.getAttribute('href'))
      .toBe('/tecnico/relatorios');
    expect(texto).not.toContain('Amostras aguardando');
  });

  it('mostra zeros e estado vazio quando a API responde sem registros', () => {
    fixture.detectChanges();
    http.expectOne('/api/technician/dashboard').flush(resposta({
      testsInExecution: 0,
      testsAwaitingApproval: 0,
      testsFinished: 0,
      pendingReportsTotal: 0,
      pendingReports: [],
    }));
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    const metricas = fixture.nativeElement.querySelectorAll(
      'section[aria-label="Indicadores do dashboard"] article p',
    ) as NodeListOf<HTMLElement>;
    const valores = Array.from(metricas, (elemento) => elemento.textContent?.trim());

    expect(valores).toEqual(['0', '0', '0']);
    expect(texto).toContain('Nenhum laudo pendente de validação');
    expect(texto).toContain('Ver todos');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('mantém os atalhos na falha e permite repetir a consulta sem exibir números antigos', () => {
    fixture.detectChanges();
    http.expectOne('/api/technician/dashboard').flush(
      { message: 'Falha na consulta' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Não foi possível carregar o dashboard');
    expect(texto).toContain('Manter ensaio');
    expect(texto).toContain('Equipamentos');
    expect(texto).not.toContain('Ensaios em execução');
    expect(fixture.nativeElement.querySelectorAll('nav[aria-label="Acesso rápido aos módulos técnicos"] a').length).toBe(4);

    const botao = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    botao.click();
    fixture.detectChanges();

    const retry = http.expectOne('/api/technician/dashboard');
    expect(retry.request.method).toBe('GET');
    retry.flush(resposta({ testsInExecution: 7 }));
    fixture.detectChanges();

    const metricas = fixture.nativeElement.querySelectorAll(
      'section[aria-label="Indicadores do dashboard"] article p',
    ) as NodeListOf<HTMLElement>;
    const valores = Array.from(metricas, (elemento) => elemento.textContent?.trim());
    expect(valores).toEqual(['7', '2', '5']);
    expect(fixture.nativeElement.textContent).not.toContain('Não foi possível carregar o dashboard');
  });
});
