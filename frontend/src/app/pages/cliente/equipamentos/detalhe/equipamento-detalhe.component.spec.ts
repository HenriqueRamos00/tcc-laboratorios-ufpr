import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { EquipamentoDetalheComponent } from './equipamento-detalhe.component';

const ENDERECO_TEMPORARIO = 'blob:relatorio-de-teste';

function montarDetalhe() {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture = TestBed.createComponent(EquipamentoDetalheComponent);
  fixture.componentRef.setInput('id', '60101');
  fixture.detectChanges();
  tick(400);
  fixture.detectChanges();
  return fixture;
}

describe('EquipamentoDetalheComponent', () => {
  it('leva cada linha do histórico para a sua própria análise, na aba certa', fakeAsync(() => {
    const fixture = montarDetalhe();

    const tela: HTMLElement = fixture.nativeElement;
    const enderecos = Array.from(tela.querySelectorAll('tbody a')).map((ancora) =>
      ancora.getAttribute('href'),
    );

    expect(enderecos[0]).toBe(
      '/cliente/equipamentos/60101/indicadores/60101-1?aba=fisico-quimico',
    );
    expect(enderecos[1]).toBe('/cliente/equipamentos/60101/indicadores/60101-2?aba=gases');
  }));

  it('baixa o relatório e revoga o endereço temporário depois do clique', fakeAsync(() => {
    const criar = spyOn(URL, 'createObjectURL').and.returnValue(ENDERECO_TEMPORARIO);
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
    expect(revogar).toHaveBeenCalledWith(ENDERECO_TEMPORARIO);
  }));
});
