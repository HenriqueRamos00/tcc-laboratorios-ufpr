import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import {
  chromatographySamplesOfEquipment,
  dissolvedGasBlockOf,
} from '@/app/model/measured-gas-sample.spec-util';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';
import { HealthService } from '@/app/services/health.service';

import { HealthIndicatorsComponent } from '../health-indicators.component';

// A suíte pode ficar inteira verde com a aba vazia: todo teste de unidade afirma
// sobre o serviço ou sobre uma figura isolada, e nenhum deles percorre o caminho
// que o cliente percorre. Aconteceu nesta integração, e só apareceu porque a tela
// foi renderizada: a aba de gases abre na sub-aba de critérios, e sem o clique em
// "Métodos de Diagnóstico" o painel não existe no DOM.
//
// Este caso entra pela tela, com gás medido de verdade, e confere que as seis
// figuras saem desenhadas.
describe('Aba de métodos de diagnóstico, com o gás medido da amostra real', () => {
  function abrirAbaDeMetodos() {
    const indicadores = {
      ...HEALTH_INDICATORS,
      dissolvedGases: dissolvedGasBlockOf(chromatographySamplesOfEquipment('571289')),
      diagnosis: null,
    };

    TestBed.configureTestingModule({
      imports: [HealthIndicatorsComponent],
      providers: [
        provideRouter([]),
        { provide: HealthService, useValue: { equipmentIndicators: () => of(indicadores) } },
      ],
    });

    const fixture = TestBed.createComponent(HealthIndicatorsComponent);
    fixture.componentRef.setInput('id', indicadores.equipmentId);
    fixture.componentRef.setInput('aba', 'gases');
    fixture.detectChanges();

    const raiz = fixture.nativeElement as HTMLElement;
    const subAba = [...raiz.querySelectorAll('button')].find((botao) =>
      (botao.textContent ?? '').includes('Métodos'),
    );

    subAba?.click();
    fixture.detectChanges();

    return { fixture, raiz };
  }

  it('desenha as seis figuras do diagnóstico', () => {
    const { raiz } = abrirAbaDeMetodos();

    expect(raiz.querySelectorAll('app-duval-triangle').length).toBe(3);
    expect(raiz.querySelectorAll('app-duval-pentagon').length).toBe(1);
    expect(raiz.querySelectorAll('app-ieee-c57104-chart').length).toBe(1);
    expect(raiz.querySelectorAll('app-nbr-7274-chart').length).toBe(1);
    expect(raiz.querySelectorAll('app-gas-ratio-table').length).toBe(2);
    expect(raiz.querySelectorAll('app-iec-60599-fault-codes').length).toBe(1);
    expect(raiz.querySelectorAll('app-diagnostic-methods svg').length).toBeGreaterThanOrEqual(6);
  });

  // O pentágono publica o que foi medido e recusa o que depende de tabela ausente.
  it('publica os percentuais medidos e declara a zona como não avaliada', () => {
    const texto = (abrirAbaDeMetodos().raiz.querySelector('app-diagnostic-methods')?.textContent ?? '')
      .replace(/\s+/g, ' ');

    expect(texto).toContain('19,4 %');
    expect(texto).toContain('0 %');
  });

  // As quatro figuras de zona da tela respondem ao cursor do mesmo jeito. O pentágono
  // destoava: zona sem realce, sem linha de dica e com os percentuais soltos fora da
  // figura. Apontar um gás acende o eixo dele, que é a pergunta de quem lê o desenho.
  it('dá ao pentágono o mesmo comportamento das figuras ternárias', () => {
    const pentagono = abrirAbaDeMetodos().raiz.querySelector('app-duval-pentagon');

    expect(pentagono).not.toBeNull();
    if (!pentagono) return;

    expect(pentagono.querySelectorAll('path[tabindex]').length).toBe(5);
    expect(pentagono.querySelectorAll('ul li').length).toBe(5);
    expect(pentagono.querySelector('p')?.textContent?.trim())
      .toBe('Passe o cursor sobre uma zona ou leitura');

    const legenda = [...pentagono.querySelectorAll('ul li')]
      .map((item) => item.textContent?.replace(/\s+/g, ' ').trim());

    expect(legenda).toContain('H₂19,4 %');
    expect(legenda).toContain('C₂H₂0 %');
  });

  // Marcação de hover existe não é hover que funciona: a primeira versão deste caso
  // sobreviveu à mutação que apagava o realce e o `mouseenter`. Aqui o evento é
  // disparado de verdade e a dica tem que mudar.
  it('responde ao cursor sobre a zona do pentágono', () => {
    const { fixture, raiz } = abrirAbaDeMetodos();
    const pentagono = raiz.querySelector('app-duval-pentagon');

    expect(pentagono?.querySelector('p')?.textContent?.trim())
      .toBe('Passe o cursor sobre uma zona ou leitura');

    const setor = pentagono?.querySelector('path[tabindex]');

    setor?.dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();

    expect(pentagono?.querySelector('p')?.textContent?.trim())
      .toBe('T2: Falha térmica entre 300 °C e 700 °C');
    expect(setor?.getAttribute('fill-opacity')).not.toBe('0');
  });

  // O retângulo de foco do navegador atravessava as zonas vizinhas, porque segue a
  // caixa do elemento e não o polígono. Ele sai, e o foco continua anunciado pelo
  // `aria-label` e pelo realce da própria figura.
  it('não desenha o retângulo de foco do navegador sobre as figuras', () => {
    const { raiz } = abrirAbaDeMetodos();
    const focaveis = [...raiz.querySelectorAll('app-duval-triangle [tabindex], app-duval-pentagon [tabindex]')];

    expect(focaveis.length).toBeGreaterThan(0);
    expect(focaveis.every((elemento) => (elemento.getAttribute('class') ?? '').includes('outline-hidden')))
      .toBeTrue();
  });
});
