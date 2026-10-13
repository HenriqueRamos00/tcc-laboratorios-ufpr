import { BreakpointObserver } from '@angular/cdk/layout';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { LINHA_DE_ETAPAS } from '@/app/model/analysis-pipeline';

import { StatusStepperComponent } from './status-stepper.component';

const ETAPAS = LINHA_DE_ETAPAS.map((etapa) => ({
  key: etapa.chave,
  label: etapa.rotulo,
  icon: etapa.icone,
}));

const VISIVEIS_NO_DESKTOP = 5;

function montarStepper(indiceAtual: number) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      // A largura da janela do navegador de teste decidiria se o stepper está
      // no modo de celular, que mostra três etapas em vez de cinco. Aqui a
      // largura é parte do caso de teste, então ela entra por substituição.
      { provide: BreakpointObserver, useValue: { observe: () => of({ matches: false }) } },
    ],
  });

  const fixture = TestBed.createComponent(StatusStepperComponent);
  fixture.componentRef.setInput('steps', ETAPAS);
  fixture.componentRef.setInput('currentIndex', indiceAtual);
  fixture.detectChanges();

  return fixture.componentInstance.visibleSteps();
}

// O processo é um só, de qualificação a relatório publicado, e o cliente
// precisa ver de onde veio e para onde vai. Por isso a etapa atual fica no meio
// da janela; as únicas fora do meio são as duas do começo da linha
// (qualificação e em análise pela área) e as duas do fim (elaborando relatório
// e relatório publicado), porque aí não há cinco etapas em volta.
describe('StatusStepperComponent, onde a etapa atual aparece', () => {
  it('mantém a etapa atual no meio da janela, menos nas duas pontas da linha', () => {
    const posicoes = ETAPAS.map((_, indice) =>
      montarStepper(indice).findIndex((etapa) => etapa.state === 'active'),
    );

    expect(posicoes).toEqual([0, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 4]);
  });

  it('sempre mostra cinco etapas, inclusive nas pontas', () => {
    const quantidades = ETAPAS.map((_, indice) => montarStepper(indice).length);

    expect(quantidades).toEqual(ETAPAS.map(() => VISIVEIS_NO_DESKTOP));
  });

  it('mostra as duas etapas anteriores e as duas seguintes ao redor da atual', () => {
    const protocolado = ETAPAS.findIndex((etapa) => etapa.key === 'protocolado');

    const rotulos = montarStepper(protocolado).map((etapa) => etapa.label);

    expect(rotulos).toEqual([
      'Aguardando entrega da amostra',
      'Recebido',
      'Protocolado',
      'Em execução',
      'Incompleto',
    ]);
  });
});
