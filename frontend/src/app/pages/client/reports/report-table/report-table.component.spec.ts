import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { API_URL } from '@/app/environment/env';
import type { Report } from '@/app/model/report';

import { ReportTableComponent } from './report-table.component';

const SPRING_REPORT: Report = {
  id: '804', identification: '804', requestNumber: 'P-52', quoteCode: 'ORC-52',
  sampleId: null, sampleName: 'AM-1', client: null, status: 'Publicado',
  scheduledDate: null, issueDate: null, publicationDate: '2026-11-07',
  hasSignedDocument: false, pdfAvailable: true, laboratory: 'Laboratório de Ensaios',
};

describe('ReportTableComponent, documento publicado pelo Spring', () => {
  function render() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(ReportTableComponent);
    fixture.componentRef.setInput('reports', [SPRING_REPORT]);
    fixture.detectChanges();
    return fixture;
  }

  it('baixa o PDF recebido mesmo sem assinatura digital e mostra o laboratório informado', () => {
    const create = spyOn(URL, 'createObjectURL').and.returnValue('blob:report');
    spyOn(URL, 'revokeObjectURL');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Laboratório de Ensaios');
    const button = element.querySelector('button')!;
    expect(button.disabled).toBeFalse();
    button.click();
    const pdf = new Blob(['%PDF-1.7\nspring'], { type: 'application/pdf' });
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_URL}/api/reports/804/document`).flush(pdf);
    fixture.detectChanges();
    expect(create).toHaveBeenCalledOnceWith(pdf);
    expect(click).toHaveBeenCalledTimes(1);
    http.verify();
  });

  it('falha no download não cria um arquivo substituto e mantém a tabela visível', () => {
    const create = spyOn(URL, 'createObjectURL');
    const fixture = render();
    const element = fixture.nativeElement as HTMLElement;
    element.querySelector('button')!.click();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(`${API_URL}/api/reports/804/document`)
      .flush(null, { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(element.querySelector('table')).not.toBeNull();
    expect(element.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível baixar');
    expect(create).not.toHaveBeenCalled();
    http.verify();
  });
});
