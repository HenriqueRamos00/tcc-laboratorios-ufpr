import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { API_URL } from '@/app/environment/env';
import type { EquipmentDetail, EquipmentReport } from '@/app/model/equipment';

import { EquipmentDetailComponent } from './equipment-detail.component';

const TEMPORARY_ADDRESS = 'blob:relatorio-de-teste';
const EQUIPMENT_ID = 'NTcxMjg5';
const HISTORY_URL = `${API_URL}/api/equipments/${EQUIPMENT_ID}/reports?page=1&pageSize=10`;

const DETAIL: EquipmentDetail = {
  id: EQUIPMENT_ID,
  serialNumber: '571289',
  tag: '571289',
  equipmentTag: 'TR-00001',
  type: 'TRANSFORMADOR',
  location: null,
  company: 'EMPRESA DE ENERGIA',
  identification: 'LACTEC-02272/2026',
  reportCount: 2,
  mostRecentReportDate: '2026-06-23',
  nameplateKnown: true,
  oilType: 'Óleo Mineral Isolante',
  voltage: '220',
  power: '50 MVA',
  manufacturer: 'FABRICANTE DE TRANSFORMADORES',
  manufacturingYear: '1979',
  oilVolume: '20570',
};

/** A série que o cadastro não conhece, que é o caso das duas séries inventadas. */
const DETAIL_WITHOUT_NAMEPLATE: EquipmentDetail = {
  ...DETAIL,
  serialNumber: '884120',
  tag: '884120',
  equipmentTag: null,
  type: null,
  nameplateKnown: false,
  oilType: null,
  voltage: null,
  power: null,
  manufacturer: null,
  manufacturingYear: null,
  oilVolume: null,
};

const HISTORY: EquipmentReport[] = [
  {
    id: 'laudo-fisico-quimico',
    date: '2026-06-23',
    analysisType: 'physical-chemical',
    status: 'completed',
    hasSignedDocument: true,
  },
  {
    id: 'laudo-cromatografia',
    date: '2026-03-18',
    analysisType: 'chromatography',
    status: 'completed',
    hasSignedDocument: true,
  },
];

function montarDetalhe() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
  });

  const fixture = TestBed.createComponent(EquipmentDetailComponent);
  fixture.componentRef.setInput('id', EQUIPMENT_ID);
  fixture.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  http.expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}`).flush(DETAIL);
  http.expectOne(HISTORY_URL).flush(HISTORY, { headers: { 'X-Total-Count': '2' } });

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

  it('não oferece download quando a API diz que não há arquivo publicado', fakeAsync(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    const fixture = TestBed.createComponent(EquipmentDetailComponent);
    fixture.componentRef.setInput('id', EQUIPMENT_ID);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}`).flush(DETAIL);
    http.expectOne(HISTORY_URL).flush([{ ...HISTORY[0], hasSignedDocument: false }]);
    fixture.detectChanges();

    const tela: HTMLElement = fixture.nativeElement;
    const botao = tela.querySelector('tbody button') as HTMLButtonElement;

    expect(botao.disabled).toBeTrue();
  }));

  /**
   * O que o cadastro informa aparece com a unidade que ele escreveu, e nenhuma frase de
   * ausência é dita quando ele conhece a série.
   */
  it('mostra o que o cadastro informa, sem anunciar ausência', fakeAsync(() => {
    const fixture = montarDetalhe();

    const tela: HTMLElement = fixture.nativeElement;
    const cartoes = Array.from(tela.querySelectorAll('dl dd')).map((celula) =>
      celula.textContent?.trim(),
    );

    expect(cartoes).toContain('TR-00001');
    expect(cartoes).toContain('TRANSFORMADOR');
    expect(cartoes).toContain('50 MVA');
    expect(cartoes).toContain('FABRICANTE DE TRANSFORMADORES');
    expect(cartoes).not.toContain('Não informado');
    expect(cartoes).toContain('Dados não disponíveis');
    expect(tela.querySelector('[role="status"]')).toBeNull();
  }));

  /**
   * A série que o cadastro não conhece diz isso uma vez, em vez de repetir "Não informado"
   * em sete cartões sem explicar por quê.
   */
  it('diz que o cadastro não conhece a série quando não há placa', fakeAsync(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });

    const fixture = TestBed.createComponent(EquipmentDetailComponent);
    fixture.componentRef.setInput('id', EQUIPMENT_ID);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}`).flush(DETAIL_WITHOUT_NAMEPLATE);
    http.expectOne(HISTORY_URL).flush(HISTORY, { headers: { 'X-Total-Count': '2' } });
    fixture.detectChanges();

    const tela: HTMLElement = fixture.nativeElement;

    expect(tela.querySelector('[role="status"]')?.textContent).toContain(
      'não tem este número de série',
    );
    expect(tela.textContent).toContain('884120');
  }));

  it('baixa o laudo assinado com o nome que a API informa e revoga o endereço depois', fakeAsync(() => {
    const criar = spyOn(URL, 'createObjectURL').and.returnValue(TEMPORARY_ADDRESS);
    const revogar = spyOn(URL, 'revokeObjectURL');
    let nomeSalvo = '';
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) {
      nomeSalvo = this.download;
    });
    const fixture = montarDetalhe();

    const tela: HTMLElement = fixture.nativeElement;
    const botao = tela.querySelector('tbody button') as HTMLButtonElement;
    botao.click();
    fixture.detectChanges();

    expect(botao.textContent).toContain('Baixando…');
    expect(botao.disabled).toBeTrue();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}/reports/laudo-fisico-quimico/document`)
      .flush(new Blob(['%PDF-1.4'], { type: 'application/pdf' }), {
        headers: { 'Content-Disposition': 'attachment; filename="LACTEC-02272-2026.pdf"' },
      });
    fixture.detectChanges();

    expect(criar.calls.mostRecent().args[0] instanceof Blob).toBeTrue();
    expect(nomeSalvo).toBe('LACTEC-02272-2026.pdf');
    expect(botao.disabled).toBeFalse();

    tick();
    expect(revogar).toHaveBeenCalledWith(TEMPORARY_ADDRESS);
  }));

  it('avisa a falha quando a API não entrega o laudo', fakeAsync(() => {
    const fixture = montarDetalhe();
    const tela: HTMLElement = fixture.nativeElement;

    (tela.querySelector('tbody button') as HTMLButtonElement).click();
    TestBed.inject(HttpTestingController)
      .expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}/reports/laudo-fisico-quimico/document`)
      .flush(new Blob(), { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(tela.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível baixar');
  }));

  it('pede ao servidor a página seguinte do histórico', fakeAsync(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const fixture = TestBed.createComponent(EquipmentDetailComponent);
    fixture.componentRef.setInput('id', EQUIPMENT_ID);
    fixture.detectChanges();

    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}`).flush(DETAIL);
    http.expectOne(HISTORY_URL).flush(HISTORY, { headers: { 'X-Total-Count': '12' } });
    fixture.detectChanges();

    const tela: HTMLElement = fixture.nativeElement;
    expect(tela.querySelector('app-pagination')?.textContent).toContain('1-10 de 12');

    const proximo = Array.from(tela.querySelectorAll('app-pagination button')).find((botao) =>
      botao.textContent?.includes('Próximo'),
    ) as HTMLButtonElement;
    proximo.click();
    fixture.detectChanges();

    http
      .expectOne(`${API_URL}/api/equipments/${EQUIPMENT_ID}/reports?page=2&pageSize=10`)
      .flush([HISTORY[1]], { headers: { 'X-Total-Count': '12' } });
    fixture.detectChanges();

    expect(tela.querySelector('app-pagination')?.textContent).toContain('11-12 de 12');
    expect(tela.querySelectorAll('tbody tr').length).toBe(1);
  }));
});
