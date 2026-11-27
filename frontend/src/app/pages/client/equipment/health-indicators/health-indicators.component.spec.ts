import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatTooltip } from '@angular/material/tooltip';
import { provideRouter } from '@angular/router';
import { of, throwError, type Observable } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import type { HealthIndicators, MeasuredValue, PublishedReport } from '@/app/model/health';
import { HEALTH_INDICATORS } from '@/app/services/fixtures/health-indicators.fixture';
import { DissolvedGasDiagnosisService } from '@/app/services/dissolved-gas-diagnosis.service';
import { EquipmentsService } from '@/app/services/equipments.service';
import { HealthService } from '@/app/services/health.service';
import type { DownloadedFile } from '@shared/file-download';

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
          limitAbsence: null,
          valuesByAnalysis,
          classification: 'normal',
        },
      ],
    },
  };
}

const COM_INDICIO_DE_FALHA: HealthIndicators = {
  ...HEALTH_INDICATORS,
  dissolvedGases: {
    ...HEALTH_INDICATORS.dissolvedGases,
    gases: HEALTH_INDICATORS.dissolvedGases.gases.map((gas) =>
      gas.key === 'h2' ? { ...gas, results: [2028, 120] } : gas.key === 'ch4' ? { ...gas, results: [601, 30] } : gas,
    ),
  },
};

let pedirArquivo: (id: string) => Observable<DownloadedFile | null> = () => of(null);

function montarCom(indicadores: HealthIndicators, aba?: string): ComponentFixture<HealthIndicatorsComponent> {
  TestBed.configureTestingModule({
    imports: [HealthIndicatorsComponent],
    providers: [
      provideRouter([]),
      {
        provide: HealthService,
        useValue: { equipmentIndicators: () => of(indicadores) },
      },
      {
        provide: EquipmentsService,
        useValue: { reportArchive: (id: string) => pedirArquivo(id) },
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
    const group = raiz.querySelector('thead th[colspan]') as HTMLElement;

    expect(group.textContent?.trim()).toBe('Histórico de resultados');
    expect(group.getAttribute('colspan')).toBe('2');
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
  it('avisa que os dados não estão disponíveis quando o laudo vem sem classificação', () => {
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

    expect(celulasDaPrimeiraLinha(raiz).at(-1)).toBe('Classificação: Dados não disponíveis');
    expect(raiz.querySelector('app-conformity-banner')?.textContent?.trim())
      .toBe('Conformidade do resultado: Dados não disponíveis');
    expect(raiz.querySelector('app-conformity-gauge')?.textContent).toContain('Dados não disponíveis');
    expect(raiz.querySelector('app-conformity-gauge ul')).toBeNull();
  });

  it('avisa que a tensão nominal não está disponível quando a placa não a informa', () => {
    const base = indicadoresCom(['2025-10-26'], [38]);
    const raiz = raizDe({
      ...base,
      physicalChemical: { ...base.physicalChemical, nominalVoltage: null },
    });
    const cabecalho = [...raiz.querySelectorAll('thead th')]
      .find((th) => th.textContent?.includes('ABNT NBR 10576/17'));

    expect(cabecalho?.querySelector('app-data-unavailable')).not.toBeNull();
    expect(cabecalho?.textContent).not.toContain('null');
  });

  it('avisa que método e valor-limite não estão disponíveis quando chegam como hífen', () => {
    const base = indicadoresCom(['2025-10-26'], [38]);
    const raiz = raizDe({
      ...base,
      physicalChemical: {
        ...base.physicalChemical,
        tests: base.physicalChemical.tests.map((test) => ({ ...test, method: '-', limit: '-' })),
      },
    });
    const celulas = celulasDaPrimeiraLinha(raiz);

    expect(celulas[1]).toBe('Método: Dados não disponíveis');
    expect(celulas[2]).toBe('Valor-limite: Dados não disponíveis');
  });

  it('diz uma vez que não há laudo publicado quando a aba não tem nenhum', () => {
    const raiz = raizDe({
      ...HEALTH_INDICATORS,
      physicalChemical: { ...HEALTH_INDICATORS.physicalChemical, reports: [] },
    });
    const regiao = raiz.querySelector('[aria-labelledby="report-title-physical-chemical"]');
    const paragraphs = [...(regiao?.querySelectorAll('p') ?? [])].map((p) => p.textContent?.trim());

    expect(regiao?.querySelector('h2')?.textContent?.trim()).toBe('Laudos');
    expect(paragraphs).toEqual(['Nenhum laudo publicado']);
    expect(regiao?.textContent).not.toContain('Dados não disponíveis');
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

  it('lista cada laudo na ordem recebida, com data, identificação, rótulo e parágrafos', () => {
    const raiz = raizDe(HEALTH_INDICATORS);
    const reports = HEALTH_INDICATORS.physicalChemical.reports;
    const regiao = raiz.querySelector('[aria-labelledby="report-title-physical-chemical"]');
    const itens = [...(regiao?.querySelectorAll('li') ?? [])];
    const primeiro = itens[0];

    expect(itens.length).toBe(reports.length);
    expect(itens.map((item) => item.querySelector('article')?.getAttribute('aria-label')))
      .toEqual(reports.map((report) => report.identification));
    const cabecalho = [...(primeiro.querySelector('p')?.querySelectorAll('span') ?? [])];

    expect(cabecalho.map((span) => span.textContent?.trim()))
      .toEqual(['30/10/2025', 'LACTEC-04809/2025', 'NORMAL']);
    expect(primeiro.querySelector('h3')?.textContent?.trim()).toBe(reports[0].title ?? undefined);
    expect([...primeiro.querySelectorAll('p')].slice(1).map((p) => p.textContent?.trim()))
      .toEqual([...reports[0].paragraphs]);
  });

  it('pinta o rótulo pela cor que o laudo trouxe, e nunca pelo texto do rótulo', () => {
    const raiz = raizDe({
      ...HEALTH_INDICATORS,
      physicalChemical: {
        ...HEALTH_INDICATORS.physicalChemical,
        reports: [
          semTexto('LACTEC-4', '2026-11-02', 'NORMAL', 'alerta'),
          semTexto('LACTEC-3', '2026-10-10', 'Rótulo de cor desconhecida', null),
          semTexto('LACTEC-2', '2026-09-24', 'Descarga elétrica', 'nao-conforme'),
          semTexto('LACTEC-1', '2026-07-28', 'normal', 'normal'),
          semTexto('LACTEC-0', '2026-05-02', null, null),
        ],
      },
    });
    const rotulos = [
      ...(raiz
        .querySelector('[aria-labelledby="report-title-physical-chemical"]')
        ?.querySelectorAll('[data-result-label]') ?? []),
    ];

    expect(rotulos.map((rotulo) => rotulo.textContent?.trim()))
      .toEqual(['NORMAL', 'Rótulo de cor desconhecida', 'Descarga elétrica', 'normal']);
    expect(rotulos[0].classList).toContain('bg-lactec-tarja-alerta');
    expect(rotulos[1].className).not.toContain('bg-lactec-tarja');
    expect(rotulos[2].classList).toContain('bg-lactec-tarja-nao-conforme');
    expect(rotulos[3].classList).toContain('bg-lactec-tarja-normal');
  });

  it('escreve o laudo como texto, sem interpretar marcação que venha no parágrafo', () => {
    const comMarcacao = {
      ...HEALTH_INDICATORS,
      physicalChemical: {
        ...HEALTH_INDICATORS.physicalChemical,
        reports: [
          {
            identification: 'LACTEC-1',
            date: '2026-07-28',
            resultLabel: null,
            conformity: null,
            title: 'Laudo',
            paragraphs: ['Óleo <b>fora</b> do limite'],
          },
        ],
      },
    };
    const regiao = raizDe(comMarcacao).querySelector(
      '[aria-labelledby="report-title-physical-chemical"]',
    );

    expect(regiao?.querySelectorAll('p')[1]?.textContent?.trim()).toBe('Óleo <b>fora</b> do limite');
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

// O backend serve `diagnosis: null` e vai continuar servindo, enquanto o núcleo
// normativo não migrar. Quem calcula os sete métodos é o
// `DissolvedGasDiagnosisService`, a partir dos gases dissolvidos do mesmo corpo,
// e a aba de métodos tem que mostrar isso: antes ela anunciava "este laudo não
// traz diagnóstico" para TODO laudo, inclusive com os nove gases na tabela ao
// lado.
//
// O bloco continua podendo não existir, e agora por um motivo só: laudo de área
// que não é Óleos não tem gás dissolvido nenhum para diagnosticar.
describe('HealthIndicatorsComponent, aba de métodos de diagnóstico', () => {
  afterEach(() => TestBed.resetTestingModule());

  const semGasDissolvido: HealthIndicators = {
    ...HEALTH_INDICATORS,
    dissolvedGases: {
      ...HEALTH_INDICATORS.dissolvedGases,
      analysisDates: [],
      analyses: [],
      gases: [],
    },
  };

  const laudoSoDeCromatografia: HealthIndicators = {
    ...HEALTH_INDICATORS,
    selectedAnalysis: {
      id: 'MDI2NDcvMjAyNi0wMDE',
      date: '2026-11-09',
      label: 'LACTEC-02647/2026',
      hasPhysicalChemical: false,
      hasDissolvedGases: true,
    },
  };

  it('trava a aba físico-química do laudo só de cromatografia e explica o motivo', () => {
    const fixture = montarCom(laudoSoDeCromatografia, 'fisico-quimico');
    const raiz = fixture.nativeElement as HTMLElement;
    const fisicoQuimico = raiz.querySelector('#aba-fisico-quimico') as HTMLButtonElement;
    const dica = fixture.debugElement.query(By.css('#aba-fisico-quimico')).injector.get(MatTooltip);

    expect(fisicoQuimico.getAttribute('aria-disabled')).toBe('true');
    expect(fisicoQuimico.classList).toContain('cursor-not-allowed');
    expect(dica.message).toBe('Indisponível pois o ensaio é de análise cromatográfica');
    expect(raiz.querySelector('#painel-fisico-quimico')).toBeNull();
    expect(raiz.querySelector('#painel-gases')).not.toBeNull();
  });

  it('deixa as duas abas livres no laudo que mediu as duas coisas', () => {
    const fixture = montarCom({
      ...laudoSoDeCromatografia,
      selectedAnalysis: { ...laudoSoDeCromatografia.selectedAnalysis!, hasPhysicalChemical: true },
    });
    const raiz = fixture.nativeElement as HTMLElement;

    expect(raiz.querySelector('[aria-disabled="true"]')).toBeNull();
    expect(raiz.querySelector('#painel-fisico-quimico')).not.toBeNull();
  });

  it('mostra o estado vazio quando o laudo escolhido não é físico-químico nem cromatografia', () => {
    const raiz = raizDe({
      ...laudoSoDeCromatografia,
      selectedAnalysis: { ...laudoSoDeCromatografia.selectedAnalysis!, hasDissolvedGases: false },
    });

    expect(raiz.querySelector('[role="tablist"]')).toBeNull();
  });

  it('desenha os três triângulos de Duval com o bloco de diagnóstico nulo no corpo', () => {
    const raiz = abrirAbaDeMetodos(COM_INDICIO_DE_FALHA);

    expect(HEALTH_INDICATORS.diagnosis).toBeNull();
    expect(raiz.querySelectorAll('app-duval-triangle').length).toBe(3);
    expect(raiz.querySelector('#painel-metodos')?.textContent)
      .not.toContain('não traz diagnóstico');
  });

  // O pentágono, a NBR 7274, a IEC 60599, Rogers e o IEEE C57.104 aparecem na
  // mesma aba: estes dois alcançam o painel inteiro, porque painel que desenha
  // triângulo e cala os outros cinco métodos é o defeito anterior pela metade.
  it('mostra os sete métodos, cada um com o seu painel', () => {
    const painel = abrirAbaDeMetodos(COM_INDICIO_DE_FALHA).querySelector('#painel-metodos');
    const texto = painel?.textContent ?? '';

    expect(painel?.querySelectorAll('app-duval-triangle').length).toBe(3);
    expect(painel?.querySelector('app-duval-pentagon')).not.toBeNull();
    expect(texto).toContain('MÉTODO IEEE C57.104');
    expect(texto).toContain('MÉTODO IEC 60599');
    expect(texto).toContain('NBR 7274');
    expect(texto).toContain('MÉTODO DE ROGERS');
  });

  // Razão calculada do gás medido, e não texto de demonstração: na coleta mais
  // recente da massa, o metano dá 3 ppm e o hidrogênio 12, e 3/12 é 0,25.
  it('escreve as razões características com o valor medido, no formato do laudo', () => {
    const painel = abrirAbaDeMetodos(COM_INDICIO_DE_FALHA).querySelector('#painel-metodos');

    expect(painel?.textContent).toContain('CH₄ / H₂');
    expect(painel?.textContent).toContain('0,25');
  });

  it('diz o que não foi avaliado, em vez de calar ou de inventar faixa', () => {
    const texto =
      abrirAbaDeMetodos(COM_INDICIO_DE_FALHA).querySelector('#painel-metodos')?.textContent ?? '';

    expect(texto).toContain('Zona Não avaliado');
    expect(texto).toContain('Faixas Não avaliado');
    expect(texto).toContain('Limite por gás Não avaliado');
    expect(texto).toContain('Código de falha Não avaliado');
  });

  it('fecha a aba com uma conclusão para cada um dos sete métodos', () => {
    const painel = abrirAbaDeMetodos(COM_INDICIO_DE_FALHA).querySelector('#painel-metodos');
    const conclusoes = [...(painel?.querySelectorAll('ul li') ?? [])]
      .map((item) => item.textContent?.trim() ?? '')
      .filter((texto) => texto.includes('>'));

    expect(conclusoes.length).toBe(7);
    expect(conclusoes[0]).toContain('Triângulo 1');
    expect(conclusoes.at(-1)).toContain('IEEE C57.104');
  });

  it('trava a aba de gases do equipamento sem cromatografia e abre a físico-química', () => {
    const fixture = montarCom(semGasDissolvido, 'gases');
    const raiz = fixture.nativeElement as HTMLElement;
    const gases = raiz.querySelector('#aba-gases') as HTMLButtonElement;

    expect(gases.getAttribute('aria-disabled')).toBe('true');
    expect(gases.classList).toContain('cursor-not-allowed');
    expect(raiz.querySelector('#painel-gases')).toBeNull();
    expect(raiz.querySelector('#painel-fisico-quimico')).not.toBeNull();

    gases.click();
    fixture.detectChanges();
    expect(raiz.querySelector('#painel-gases')).toBeNull();
  });

  // No dia em que o núcleo normativo migrar, o bloco servido vence o cálculo da
  // tela. Sem este caso, a tela poderia passar a ignorar o corpo da API e
  // nenhum teste perceberia.
  it('prefere o bloco que a API servir ao que a própria tela calcularia', () => {
    const calculado = new DissolvedGasDiagnosisService().diagnose(
      COM_INDICIO_DE_FALHA.dissolvedGases,
      COM_INDICIO_DE_FALHA.oilType,
    );
    if (calculado?.kind !== 'applied') throw new Error('A massa com indício de falha deveria render diagnóstico');
    const servido: HealthIndicators = {
      ...HEALTH_INDICATORS,
      diagnosis: {
        ...calculado,
        conclusions: [
          { method: 'Triângulo 1', code: 'PD', description: 'Veredito que veio da API' },
        ],
      },
    };

    expect(abrirAbaDeMetodos(servido).querySelector('#painel-metodos')?.textContent)
      .toContain('Veredito que veio da API');
  });
});

const CASO_REAL: HealthIndicators = {
  ...HEALTH_INDICATORS,
  dissolvedGases: {
    ...HEALTH_INDICATORS.dissolvedGases,
    analysisDates: ['2024-02-05', '2024-08-12', '2025-02-17', '2025-08-25'],
    analyses: [],
    gases: [
      { key: 'h2', name: 'Hidrogênio', formula: 'H₂', results: [20, 8, 31, 36] },
      { key: 'o2', name: 'Oxigênio', formula: 'O₂', results: [7168, 23262, 19703, 22513] },
      { key: 'n2', name: 'Nitrogênio', formula: 'N₂', results: [20799, 73699, 61548, 59148] },
      { key: 'ch4', name: 'Metano', formula: 'CH₄', results: [1, 1, 1, 1] },
      { key: 'co', name: 'Monóxido de Carbono', formula: 'CO', results: [42, 61, 72, 88] },
      { key: 'co2', name: 'Dióxido de Carbono', formula: 'CO₂', results: [257, 327, 305, 356] },
      { key: 'c2h4', name: 'Etileno', formula: 'C₂H₄', results: ['ND', 'ND', 'ND', 'ND'] },
      { key: 'c2h6', name: 'Etano', formula: 'C₂H₆', results: ['ND', 'ND', 'ND', 'ND'] },
      { key: 'c2h2', name: 'Acetileno', formula: 'C₂H₂', results: ['ND', 'ND', 'ND', 'ND'] },
    ],
  },
};

describe('HealthIndicatorsComponent, gases abaixo dos valores típicos e tipo do óleo', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('no caso real de quatro coletas desenha os métodos e avisa que as indicações não foram expressivas', () => {
    const painel = abrirAbaDeMetodos(CASO_REAL).querySelector('#painel-metodos');
    expect(painel?.querySelectorAll('app-duval-triangle').length).toBe(3);
    expect(painel?.textContent).toContain('não foram expressivas e servem apenas de apoio ao diagnóstico');
  });

  it('com gás de falha acima do valor típico não mostra o aviso de indicações não expressivas', () => {
    const painel = abrirAbaDeMetodos(COM_INDICIO_DE_FALHA).querySelector('#painel-metodos');

    expect(painel?.textContent).not.toContain('não foram expressivas');
  });

  it('em óleo vegetal mostra o tipo e declara os métodos de mineral não aplicáveis', () => {
    const vegetal: HealthIndicators = {
      ...COM_INDICIO_DE_FALHA,
      oilType: { kind: 'natural-ester', reported: 'Óleo Vegetal Isolante' },
    };
    const raiz = abrirAbaDeMetodos(vegetal);
    const texto = raiz.querySelector('#painel-metodos')?.textContent ?? '';

    expect(raiz.textContent).toContain('Tipo do óleo: Óleo vegetal (éster natural)');
    expect(raiz.querySelector('#aba-metodos')?.textContent).toContain('Métodos de Diagnóstico (Vegetal)');
    expect(texto).toContain('Método não aplicável a este tipo de óleo');
    expect(raiz.querySelectorAll('app-duval-triangle').length).toBe(0);
  });

  it('sem tipo do óleo informado não assume mineral', () => {
    const raiz = abrirAbaDeMetodos({
      ...COM_INDICIO_DE_FALHA,
      oilType: { kind: 'not-informed', reported: null },
    });

    expect(raiz.textContent).toContain('Tipo do óleo: Tipo do óleo não informado');
    expect(raiz.querySelector('#aba-metodos')?.textContent).not.toContain('Mineral');
    expect(raiz.querySelector('#painel-metodos')?.textContent).toContain('Tipo do óleo não informado');
    expect(raiz.querySelectorAll('app-duval-triangle').length).toBe(0);
  });

  it('sem critério para o tipo do óleo, a tabela físico-química diz isso no lugar do limite', () => {
    const raiz = raizDe({
      ...indicadoresCom(['2025-10-26'], [38]),
      oilType: { kind: 'silicone', reported: 'Silicone' },
      physicalChemical: {
        ...indicadoresCom(['2025-10-26'], [38]).physicalChemical,
        tests: indicadoresCom(['2025-10-26'], [38]).physicalChemical.tests.map((test) => ({
          ...test,
          limit: null,
          limitAbsence: 'oil-type-without-criterion' as const,
        })),
      },
    });

    expect(celulasDaPrimeiraLinha(raiz)).toContain('Sem critério para este tipo de óleo');
    expect(raiz.querySelector('thead')?.textContent).not.toContain('NBR 10576');
  });
});

describe('HealthIndicatorsComponent, ZIP de laudos', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    pedirArquivo = () => of(null);
  });

  function botao(raiz: HTMLElement): HTMLButtonElement {
    return raiz.querySelector('[data-tour="indicadores-baixar"] button') as HTMLButtonElement;
  }

  it('baixa o ZIP do equipamento com o nome que a API mandou', () => {
    const create = spyOn(URL, 'createObjectURL').and.returnValue('blob:zip');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    const pedidos: string[] = [];
    pedirArquivo = (id) => {
      pedidos.push(id);
      return of({ content: new Blob(['PK'], { type: 'application/zip' }), name: 'laudos-571289.zip' });
    };
    const fixture = montarCom(HEALTH_INDICATORS);

    botao(fixture.nativeElement).click();
    fixture.detectChanges();

    expect(pedidos).toEqual([HEALTH_INDICATORS.equipmentId]);
    expect(create).toHaveBeenCalled();
    expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe('laudos-571289.zip');
  });

  it('avisa quando o equipamento não tem laudo em PDF', () => {
    pedirArquivo = () => of(null);
    const fixture = montarCom(HEALTH_INDICATORS);

    botao(fixture.nativeElement).click();
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('ainda não tem laudo em PDF');
  });

  it('explica o teto quando a API recusa com 422, e a falha comum com outra frase', () => {
    pedirArquivo = () => throwError(() => new HttpErrorResponse({ status: 422 }));
    const fixture = montarCom(HEALTH_INDICATORS);
    const raiz = fixture.nativeElement as HTMLElement;

    botao(raiz).click();
    fixture.detectChanges();
    expect(raiz.querySelector('[role="alert"]')?.textContent).toContain('laudos demais');

    pedirArquivo = () => throwError(() => new HttpErrorResponse({ status: 502 }));
    botao(raiz).click();
    fixture.detectChanges();
    expect(raiz.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível baixar os laudos');
    expect(botao(raiz).disabled).toBeFalse();
  });
});

function semTexto(
  identification: string,
  date: string,
  resultLabel: string | null,
  conformity: PublishedReport['conformity'],
): PublishedReport {
  return { identification, date, resultLabel, conformity, title: null, paragraphs: [] };
}
