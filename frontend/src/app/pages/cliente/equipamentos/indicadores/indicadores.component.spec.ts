import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { IndicadoresDeSaude, ValorMedido } from '@/app/model/health';
import { EquipmentsService } from '@/app/services/equipments.service';
import { INDICADORES } from '@/app/services/fixtures/indicadores.fixture';
import { HealthService } from '@/app/services/health.service';

import { IndicadoresComponent } from './indicadores.component';

// A tabela do laudo é posicional: a coluna n mostra o valor n. Os testes abaixo
// prendem as duas maneiras de essa promessa quebrar em silêncio - o grupo
// HISTÓRICO sobrando quando só existe uma coleta, e o valor que escorrega para
// debaixo da data errada quando a linha vem mais curta que as colunas.

function indicadoresCom(
  datas: readonly string[],
  valoresPorColeta: readonly ValorMedido[],
): IndicadoresDeSaude {
  return {
    ...INDICADORES,
    fisicoQuimico: {
      ...INDICADORES.fisicoQuimico,
      colunas: datas.map((data, indice) => ({
        data,
        ehResultadoAtual: indice === datas.length - 1,
      })),
      ensaios: [
        {
          chave: 'agua',
          nome: 'Teor de Água (ppm m/m)',
          metodo: 'NBR 10710',
          limite: 'máx. 40',
          valoresPorColeta,
          classificacao: 'normal',
        },
      ],
    },
  };
}

function montarCom(indicadores: IndicadoresDeSaude): HTMLElement {
  TestBed.configureTestingModule({
    imports: [IndicadoresComponent],
    providers: [
      provideRouter([]),
      {
        provide: HealthService,
        useValue: { indicadoresDoEquipamento: () => of(indicadores) },
      },
      {
        provide: EquipmentsService,
        useValue: { buscarPorId: () => of({ tag: indicadores.tag }) },
      },
    ],
  });
  const fixture = TestBed.createComponent(IndicadoresComponent);
  fixture.componentRef.setInput('id', indicadores.equipamentoId);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function celulasDaPrimeiraLinha(raiz: HTMLElement): string[] {
  const linha = raiz.querySelector('tbody tr') as HTMLTableRowElement;
  return [...linha.cells].map((celula) => celula.textContent?.trim() ?? '');
}

describe('IndicadoresComponent, tabela físico-química', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('omite o grupo Histórico inteiro quando só existe uma coleta', () => {
    const raiz = montarCom(indicadoresCom(['26/10/2025'], [38]));
    const cabecalhos = [...raiz.querySelectorAll('thead th')];

    expect(cabecalhos.some((th) => th.textContent?.includes('Histórico'))).toBeFalse();
    expect(cabecalhos.some((th) => th.hasAttribute('colspan'))).toBeFalse();
    expect(celulasDaPrimeiraLinha(raiz)).toEqual([
      'Teor de Água (ppm m/m)',
      'NBR 10710',
      'máx. 40',
      '38',
      'Normal',
    ]);
  });

  it('cobre com o grupo Histórico exatamente as coletas anteriores à atual', () => {
    const raiz = montarCom(indicadoresCom(['20/04/2021', '20/05/2022', '26/10/2025'], [25, 32, 38]));
    const grupo = raiz.querySelector('thead th[colspan]') as HTMLElement;

    expect(grupo.textContent?.trim()).toBe('Histórico de resultados');
    expect(grupo.getAttribute('colspan')).toBe('2');
    expect(celulasDaPrimeiraLinha(raiz)).toContain('25');
    expect(celulasDaPrimeiraLinha(raiz)).toContain('32');
  });

  it('separa ensaio não detectado de coleta não realizada', () => {
    const raiz = montarCom(indicadoresCom(['20/04/2021', '20/05/2022', '26/10/2025'], [null, 'ND', 38]));

    expect(celulasDaPrimeiraLinha(raiz)).toEqual([
      'Teor de Água (ppm m/m)',
      'NBR 10710',
      'máx. 40',
      '-',
      'ND',
      '38',
      'Normal',
    ]);
  });

  it('abre lacuna no fim, e não desloca valores, quando a linha vem mais curta', () => {
    const raiz = montarCom(indicadoresCom(['20/04/2021', '20/05/2022', '26/10/2025'], [25]));
    const celulas = celulasDaPrimeiraLinha(raiz);

    expect(celulas[3]).toBe('25');
    expect(celulas[4]).toBe('-');
    expect(celulas[5]).toBe('-');
  });

  it('mostra o laudo em parágrafos, um <p> por parágrafo', () => {
    const raiz = montarCom(INDICADORES);
    const laudo = INDICADORES.fisicoQuimico.laudo;
    const regiao = raiz.querySelector('[aria-labelledby="titulo-do-laudo-fisico-quimico"]');
    const paragrafos = [...(regiao?.querySelectorAll('p') ?? [])].map((p) => p.textContent?.trim());

    expect(regiao?.querySelector('h2')?.textContent?.trim()).toBe(laudo.titulo);
    expect(paragrafos).toEqual(laudo.paragrafos.map((paragrafo) => paragrafo));
  });

  it('escreve o laudo como texto, sem interpretar marcação que venha no parágrafo', () => {
    const comMarcacao = {
      ...INDICADORES,
      fisicoQuimico: {
        ...INDICADORES.fisicoQuimico,
        laudo: { titulo: 'Laudo', paragrafos: ['Óleo <b>fora</b> do limite'] },
      },
    };
    const regiao = montarCom(comMarcacao).querySelector(
      '[aria-labelledby="titulo-do-laudo-fisico-quimico"]',
    );

    expect(regiao?.querySelector('p')?.textContent?.trim()).toBe('Óleo <b>fora</b> do limite');
    expect(regiao?.querySelector('b')).toBeNull();
  });

  it('mantém cada linha com tantos valores quantas forem as colunas', () => {
    const quantidadeDeColunas = INDICADORES.fisicoQuimico.colunas.length;

    for (const ensaio of INDICADORES.fisicoQuimico.ensaios) {
      expect(ensaio.valoresPorColeta.length)
        .withContext(ensaio.chave)
        .toBe(quantidadeDeColunas);
    }
    expect(INDICADORES.fisicoQuimico.colunas.filter((coluna) => coluna.ehResultadoAtual).length)
      .toBe(1);
    expect(INDICADORES.fisicoQuimico.colunas.at(-1)?.ehResultadoAtual).toBeTrue();
  });
});
