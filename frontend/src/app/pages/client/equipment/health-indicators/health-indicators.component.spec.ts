import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import type { HealthIndicators, MeasuredValue } from '@/app/model/health';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';
import { HealthService } from '@/app/services/health.service';

import { HealthIndicatorsComponent } from './health-indicators.component';

// A tabela do laudo é posicional: a coluna n mostra o valor n. Os testes abaixo
// prendem as duas maneiras de essa promessa quebrar em silêncio - o grupo
// HISTÓRICO sobrando quando só existe uma coleta, e o valor que escorrega para
// debaixo da data errada quando a linha vem mais curta que as colunas.

function indicadoresCom(
  datas: readonly string[],
  valuesByAnalysis: readonly MeasuredValue[],
): HealthIndicators {
  return {
    ...HEALTH_INDICATORS,
    physicalChemical: {
      ...HEALTH_INDICATORS.physicalChemical,
      columns: datas.map((data, index) => ({
        date: data,
        isCurrentResult: index === datas.length - 1,
      })),
      tests: [
        {
          key: 'agua',
          name: 'Teor de Água (ppm m/m)',
          method: 'NBR 10710',
          limit: 'máx. 40',
          valuesByAnalysis,
          classification: 'normal',
        },
      ],
    },
  };
}

function montarCom(indicadores: HealthIndicators, aba?: string): ComponentFixture<HealthIndicatorsComponent> {
  TestBed.configureTestingModule({
    imports: [HealthIndicatorsComponent],
    providers: [
      provideRouter([]),
      {
        provide: HealthService,
        useValue: { equipmentIndicators: () => of(indicadores) },
      },
    ],
  });
  const fixture = TestBed.createComponent(HealthIndicatorsComponent);
  fixture.componentRef.setInput('id', indicadores.equipmentId);
  if (aba !== undefined) fixture.componentRef.setInput('aba', aba);
  fixture.detectChanges();
  return fixture;
}

function raizDe(indicadores: HealthIndicators): HTMLElement {
  return montarCom(indicadores).nativeElement as HTMLElement;
}

// A aba de métodos é a única que lê o bloco de diagnóstico, e ela não é a aba
// padrão: alcançá-la pede o parâmetro de consulta mais o clique na sub-aba.
function abrirAbaDeMetodos(indicadores: HealthIndicators): HTMLElement {
  const fixture = montarCom(indicadores, 'gases');
  const raiz = fixture.nativeElement as HTMLElement;

  (raiz.querySelector('#aba-metodos') as HTMLButtonElement).click();
  fixture.detectChanges();

  return raiz;
}

function celulasDaPrimeiraLinha(raiz: HTMLElement): string[] {
  const row = raiz.querySelector('tbody tr') as HTMLTableRowElement;
  return [...row.cells].map((cell) => cell.textContent?.trim() ?? '');
}

describe('HealthIndicatorsComponent, tabela físico-química', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('omite o grupo Histórico inteiro quando só existe uma coleta', () => {
    const raiz = raizDe(indicadoresCom(['2025-10-26'], [38]));
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
    const raiz = raizDe(indicadoresCom(['2021-04-20', '2022-05-20', '2025-10-26'], [25, 32, 38]));
    const grupo = raiz.querySelector('thead th[colspan]') as HTMLElement;

    expect(grupo.textContent?.trim()).toBe('Histórico de resultados');
    expect(grupo.getAttribute('colspan')).toBe('2');
    expect(celulasDaPrimeiraLinha(raiz)).toContain('25');
    expect(celulasDaPrimeiraLinha(raiz)).toContain('32');
  });

  // Coluna sem data é coluna anônima: o valor aparece, mas debaixo de nada. Já
  // aconteceu, lendo um campo que o corpo da resposta não tem, e a tela só ficou
  // em branco porque interpolação de propriedade inexistente não reclama.
  //
  // A data chega em ISO, que é o formato do fio, e sai em dia/mês/ano, que é como o
  // cliente lê o laudo impresso. Publicar o ISO cru na cabeçalho seria entregar ao
  // cliente o formato da máquina.
  it('escreve a data de cada coleta no cabeçalho da coluna, no formato de quem lê', () => {
    const raiz = raizDe(indicadoresCom(['2021-04-20', '2022-05-20', '2025-10-26'], [25, 32, 38]));
    const datas = [...raiz.querySelectorAll('thead tr:last-child th')]
      .map((th) => th.textContent?.trim());

    expect(datas).toEqual(['20/04/2021', '20/05/2022', '26/10/2025']);
  });

  // O AutoLAB entrega valor medido, não parecer: enquanto o núcleo normativo não
  // roda, `classificacao`, `conformidade` e `veredito` chegam nulos em todo laudo
  // físico-químico. A tela precisa dizer isso, e não calar nem quebrar.
  it('diz que o ensaio não foi avaliado quando o laudo vem sem classificação', () => {
    const semParecer = indicadoresCom(['2025-10-26'], [38]);
    const raiz = raizDe({
      ...semParecer,
      physicalChemical: {
        ...semParecer.physicalChemical,
        conformity: null,
        verdict: null,
        tests: semParecer.physicalChemical.tests.map((test) => ({ ...test, classification: null })),
      },
    });

    expect(celulasDaPrimeiraLinha(raiz).at(-1)).toBe('Não avaliado');
    expect(raiz.querySelector('app-conformity-banner')?.textContent?.trim())
      .toBe('Não avaliado');
    expect(raiz.querySelector('app-conformity-gauge')?.textContent).toContain('Não avaliado');
  });

  it('separa ensaio não detectado de coleta não realizada', () => {
    const raiz = raizDe(indicadoresCom(['2021-04-20', '2022-05-20', '2025-10-26'], [null, 'ND', 38]));

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
    const raiz = raizDe(indicadoresCom(['2021-04-20', '2022-05-20', '2025-10-26'], [25]));
    const cells = celulasDaPrimeiraLinha(raiz);

    expect(cells[3]).toBe('25');
    expect(cells[4]).toBe('-');
    expect(cells[5]).toBe('-');
  });

  it('mostra o laudo em parágrafos, um <p> por parágrafo', () => {
    const raiz = raizDe(HEALTH_INDICATORS);
    const report = HEALTH_INDICATORS.physicalChemical.report;
    const regiao = raiz.querySelector('[aria-labelledby="report-title-physical-chemical"]');
    const paragraphs = [...(regiao?.querySelectorAll('p') ?? [])].map((p) => p.textContent?.trim());

    expect(regiao?.querySelector('h2')?.textContent?.trim()).toBe(report.title);
    expect(paragraphs).toEqual(report.paragraphs.map((paragraph) => paragraph));
  });

  it('escreve o laudo como texto, sem interpretar marcação que venha no parágrafo', () => {
    const comMarcacao = {
      ...HEALTH_INDICATORS,
      physicalChemical: {
        ...HEALTH_INDICATORS.physicalChemical,
        report: { title: 'Laudo', paragraphs: ['Óleo <b>fora</b> do limite'] },
      },
    };
    const regiao = raizDe(comMarcacao).querySelector(
      '[aria-labelledby="report-title-physical-chemical"]',
    );

    expect(regiao?.querySelector('p')?.textContent?.trim()).toBe('Óleo <b>fora</b> do limite');
    expect(regiao?.querySelector('b')).toBeNull();
  });

  it('mantém cada linha com tantos valores quantas forem as colunas', () => {
    const quantidadeDeColunas = HEALTH_INDICATORS.physicalChemical.columns.length;

    for (const test of HEALTH_INDICATORS.physicalChemical.tests) {
      expect(test.valuesByAnalysis.length)
        .withContext(test.key)
        .toBe(quantidadeDeColunas);
    }
    expect(HEALTH_INDICATORS.physicalChemical.columns.filter((column) => column.isCurrentResult).length)
      .toBe(1);
    expect(HEALTH_INDICATORS.physicalChemical.columns.at(-1)?.isCurrentResult).toBeTrue();
  });
});

// O backend serve `diagnostico: null` enquanto o núcleo normativo não migrou, e
// vai continuar servindo nulo em laudo de área que não tem gás dissolvido. O
// componente lia `dados()?.diagnostico.rogers`, e o `?.` curto-circuita só em
// `dados()`: com o envelope presente e o bloco nulo, a leitura seguinte
// derrubava a aba inteira com TypeError, sem estado de erro e sem mensagem.
describe('HealthIndicatorsComponent, laudo sem bloco de diagnóstico', () => {
  afterEach(() => TestBed.resetTestingModule());

  const semDiagnostico: HealthIndicators = { ...HEALTH_INDICATORS, diagnosis: null };

  it('abre a aba de métodos sem derrubar a tela', () => {
    const raiz = abrirAbaDeMetodos(semDiagnostico);

    expect(raiz.querySelector('#painel-metodos')).not.toBeNull();
    expect(raiz.querySelector('app-duval-triangle')).toBeNull();
  });

  it('diz que o laudo não traz diagnóstico, em vez de mostrar painel vazio', () => {
    const painel = abrirAbaDeMetodos(semDiagnostico).querySelector('#painel-metodos');

    expect(painel?.textContent).toContain('não traz diagnóstico');
  });

  it('continua desenhando os métodos quando o bloco vem preenchido', () => {
    const raiz = abrirAbaDeMetodos(HEALTH_INDICATORS);

    expect(raiz.querySelectorAll('app-duval-triangle').length)
      .toBe(HEALTH_INDICATORS.diagnosis!.triangles.length);
  });
});
