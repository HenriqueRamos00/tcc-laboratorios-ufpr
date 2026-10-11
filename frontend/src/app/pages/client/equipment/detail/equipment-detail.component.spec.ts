import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { delay, of } from 'rxjs';

import { API_URL } from '@/app/environment/env';
import type { EquipmentDetail, EquipmentReport } from '@/app/model/equipment';
import { ReportsService } from '@/app/services/reports.service';

import { EquipmentDetailComponent } from './equipment-detail.component';

const TEMPORARY_ADDRESS = 'blob:relatorio-de-teste';
const EQUIPMENT_ID = 'NTcxMjg5';

const DETAIL: EquipmentDetail = {
  id: EQUIPMENT_ID,
  serialNumber: '571289',
  tag: '571289',
  type: null,
  location: null,
  company: 'EMPRESA DE ENERGIA',
  identification: 'LACTEC-02272/2026',
  reportCount: 2,
  mostRecentReportDate: '2026-06-23',
  oilType: null,
  voltage: null,
  power: null,
};

const HISTORY: EquipmentReport[] = [
  {
    id: 'laudo-fisico-quimico',
    date: '2026-06-23',
    analysisType: 'physical-chemical',
    status: 'completed',
    reportUrl: '/api/reports/laudo-fisico-quimico/file',
  },
  {
    id: 'laudo-cromatografia',
    date: '2026-03-18',
    analysisType: 'chromatography',
    status: 'completed',
    reportUrl: '/api/reports/laudo-cromatografia/file',
  },
];

function montarDetalhe() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      {
        provide: ReportsService,
        useValue: {
          baixarRelatorio: () => of(new Blob(['%PDF-1.7'], { type: 'application/pdf' }))
            .pipe(delay(300)),
        },
      },
    ],
  });

  const fixture = TestBed.createComponent(EquipmentDetailComponent);
  fixture.componentRef.setInput('id', EQUIPMENT_ID);
  fixture.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  http.expectOne(`${API_URL}/equipments/${EQUIPMENT_ID}`).flush(DETAIL);
  http.expectOne(`${API_URL}/equipments/${EQUIPMENT_ID}/reports`).flush(HISTORY);

  fixture.detectChanges();
  return fixture;
}

describe('EquipmentDetailComponent', () => {
  it('leva cada linha do histórico para a sua própria análise, na aba certa', fakeAsync(() => {
    const fixture = montarDetalhe();

    const tela: HTMLElement = fixture.nativeElement;
    const enderecos = Array.from(tela.querySelectorAll('tbody a')).map((ancora) =>
      ancora.getAttribute('href'),
    );

    expect(enderecos[0]).toBe(
      `/cliente/equipamentos/${EQUIPMENT_ID}/indicadores/laudo-fisico-quimico?aba=fisico-quimico`,
    );
    expect(enderecos[1]).toBe(
      `/cliente/equipamentos/${EQUIPMENT_ID}/indicadores/laudo-cromatografia?aba=gases`,
    );
  }));

  /**
   * O AutoLAB desta fatia não serve o PDF, então a API devolve `reportUrl` nulo e a tela
   * não oferece download. Este caso afirma isso com a resposta real da API, e não com massa
   * escrita no componente.
   */
  it('não oferece download quando a API diz que não há arquivo publicado', fakeAsync(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    const fixture = TestBed.createComponent(EquipmentDetailComponent);
    fixture.componentRef.setInput('id', EQUIPMENT_ID);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_URL}/equipments/${EQUIPMENT_ID}`).flush(DETAIL);
    http
      .expectOne(`${API_URL}/equipments/${EQUIPMENT_ID}/reports`)
      .flush([{ ...HISTORY[0], reportUrl: null }]);
    fixture.detectChanges();

    const tela: HTMLElement = fixture.nativeElement;
    const botao = tela.querySelector('tbody button') as HTMLButtonElement;

    expect(botao.disabled).toBeTrue();
  }));

  it('baixa o relatório e revoga o endereço temporário depois do clique', fakeAsync(() => {
    const criar = spyOn(URL, 'createObjectURL').and.returnValue(TEMPORARY_ADDRESS);
    const revogar = spyOn(URL, 'revokeObjectURL');
    const clicar = spyOn(HTMLAnchorElement.prototype, 'click');
    const fixture = montarDetalhe();

    const tela: HTMLElement = fixture.nativeElement;
    const botao = tela.querySelector('tbody button') as HTMLButtonElement;
    botao.click();
    fixture.detectChanges();

    expect(botao.textContent).toContain('Baixando…');
    expect(botao.disabled).toBeTrue();

    tick(400);
    fixture.detectChanges();

    expect(criar.calls.mostRecent().args[0] instanceof Blob).toBeTrue();
    expect(clicar).toHaveBeenCalled();
    expect(botao.disabled).toBeFalse();

    tick();
    expect(revogar).toHaveBeenCalledWith(TEMPORARY_ADDRESS);
  }));
});
