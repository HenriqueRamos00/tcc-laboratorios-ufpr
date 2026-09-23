// Resultados, datas e traçado dos gráficos vêm das telas aprovadas.
//
// As zonas dos três triângulos de Duval saíram daqui: agora são derivadas das
// tabelas normativas em `model/duval-1.ts`, `duval-4.ts` e `duval-5.ts`. As
// dos triângulos 4 e 5 eram desenhadas "pelo layout da tela" e davam
// diagnóstico diferente do publicado em 68,0% e 67,5% da área — ver a
// proveniência no topo de cada módulo.
//
// Os triângulos 4 e 5 também passaram a respeitar a regra de aplicabilidade:
// são refinamentos condicionais do triângulo 1, não uma segunda opinião.

import {
  classificarDuval1,
  duval1,
  gasAusenteDoDuval1,
  zonasDuval1Com,
  DESCRICAO_DE_DUVAL_1,
  type CodigoDeDuval1,
} from '@/app/model/duval-1';
import {
  aplicaSeDuval4,
  classificarDuval4,
  duval4,
  gasAusenteDoDuval4,
  zonasDuval4Com,
  DESCRICAO_DE_DUVAL_4,
  MOTIVO_DUVAL_4_NAO_APLICAVEL,
  type CodigoDeDuval4,
} from '@/app/model/duval-4';
import {
  aplicaSeDuval5,
  classificarDuval5,
  duval5,
  gasAusenteDoDuval5,
  zonasDuval5Com,
  DESCRICAO_DE_DUVAL_5,
  MOTIVO_DUVAL_5_NAO_APLICAVEL,
  type CodigoDeDuval5,
} from '@/app/model/duval-5';
import type {
  CodigoDeFalha,
  Coleta,
  ConclusaoDeDiagnostico,
  IndicadoresDeSaude,
  LeituraDoTriangulo,
  PontoTernario,
} from '@/app/model/health';

const DATAS_FQ = ['20/04/2021', '20/05/2022', '19/09/2023', '26/04/2024', '26/10/2025'];


// --- Triângulo 1 derivado das coletas ---------------------------------------
//
// Os pontos NÃO são chumbados: saem dos mesmos ppm que alimentam a tabela de
// gases, senão o desenho e a tabela divergem sem ninguém notar. Três das cinco
// coletas não têm C₂H₂ reportado e por isso não viram ponto — elas continuam
// na lista, como `incomputavel`, para a tela poder dizer isso em voz alta.
const COLETAS_DE_GASES: readonly Coleta[] = [
      {
        data: DATAS_FQ[0],
        valores: {
          h2: 4300, o2: 5100, n2: 55000, ch4: 110, co: 270, co2: 4500, c2h4: 2400, c2h6: 14, c2h2: null,
        },
      },
      {
        data: DATAS_FQ[1],
        valores: {
          h2: 590, o2: 2400, n2: 68000, ch4: 68, co: 250, co2: 3500, c2h4: 150, c2h6: 10, c2h2: null,
        },
      },
      {
        data: DATAS_FQ[2],
        valores: {
          h2: 1030, o2: 3100, n2: 53000, ch4: 250, co: 280, co2: 6000, c2h4: 280, c2h6: 690, c2h2: null,
        },
      },
      {
        data: DATAS_FQ[3],
        valores: {
          h2: 18500, o2: 5000, n2: 59000, ch4: 250, co: 290, co2: 5100, c2h4: 380, c2h6: 680, c2h2: 2,
        },
      },
      {
        data: DATAS_FQ[4],
        valores: {
          h2: 44000, o2: 4300, n2: 47000, ch4: 3600, co: 330, co2: 6200, c2h4: 3800, c2h6: 2400, c2h2: 26,
        },
      },
];

function leiturasDoDuval1(coletas: readonly Coleta[]): readonly LeituraDoTriangulo[] {
  return coletas.map((coleta) => {
    const gases = {
      ch4: coleta.valores['ch4'] ?? null,
      c2h4: coleta.valores['c2h4'] ?? null,
      c2h2: coleta.valores['c2h2'] ?? null,
    };
    const ponto = duval1(gases);
    return ponto
      ? ({ tipo: 'plotada', data: coleta.data, ponto } as const)
      : ({
          tipo: 'incomputavel',
          data: coleta.data,
          gasAusente: gasAusenteDoDuval1(gases) ?? 'gás',
        } as const);
  });
}

const LEITURAS_DUVAL_1 = leiturasDoDuval1(COLETAS_DE_GASES);

// Um código por coleta, alinhado com COLETAS_DE_GASES; `null` quando a coleta
// não deu para computar. É esta lista que licencia os triângulos 4 e 5 —
// coleta a coleta, porque a licença é por falha diagnosticada, não pelo lote.
const CODIGO_DUVAL_1_POR_COLETA: readonly (CodigoDeDuval1 | null)[] = LEITURAS_DUVAL_1.map(
  (leitura) => (leitura.tipo === 'plotada' ? classificarDuval1(leitura.ponto) : null),
);

const CODIGOS_DUVAL_1: readonly CodigoDeDuval1[] = CODIGO_DUVAL_1_POR_COLETA.filter(
  (codigo): codigo is CodigoDeDuval1 => codigo !== null,
);

// --- Triângulos 4 e 5, condicionados ao triângulo 1 -------------------------
//
// IEEE Std C57.104-2019, p. 66: o triângulo 4 só vale depois de PD, T1 ou T2;
// o 5, depois de T2 ou T3; nenhum dos dois vale para D1 ou D2. Como a
// geometria cobre o simplex inteiro, os dois SEMPRE devolvem uma zona — é por
// isso que o portão precisa estar no código, e não no bom senso de quem lê.

/**
 * Deriva as leituras de um triângulo condicional das mesmas coletas que
 * alimentam a tabela de gases. Três desfechos, e os três continuam na lista:
 * plotada, incomputável (faltou gás) e não aplicável (sobra gás, falta
 * licença do triângulo 1).
 *
 * Parâmetros em objeto de propósito: o defeito de origem deste projeto foi
 * troca de eixo invisível ao compilador, e função com vários posicionais do
 * mesmo tipo é exatamente como isso volta.
 */
function leiturasCondicionais<G>(opcoes: {
  readonly coletas: readonly Coleta[];
  readonly gasesDa: (coleta: Coleta) => G;
  readonly aplicaSe: (codigo: CodigoDeDuval1 | null) => boolean;
  readonly motivo: string;
  readonly paraPonto: (gases: G) => PontoTernario | null;
  readonly gasAusente: (gases: G) => string | null;
}): readonly LeituraDoTriangulo[] {
  const { coletas, gasesDa, aplicaSe, motivo, paraPonto, gasAusente } = opcoes;
  return coletas.map((coleta, indice) => {
    if (!aplicaSe(CODIGO_DUVAL_1_POR_COLETA[indice])) {
      return { tipo: 'naoAplicavel', data: coleta.data, motivo } as const;
    }
    const gases = gasesDa(coleta);
    const ponto = paraPonto(gases);
    return ponto
      ? ({ tipo: 'plotada', data: coleta.data, ponto } as const)
      : ({
          tipo: 'incomputavel',
          data: coleta.data,
          gasAusente: gasAusente(gases) ?? 'gás',
        } as const);
  });
}

const LEITURAS_DUVAL_4 = leiturasCondicionais({
  coletas: COLETAS_DE_GASES,
  gasesDa: (coleta) => ({
    h2: coleta.valores['h2'] ?? null,
    ch4: coleta.valores['ch4'] ?? null,
    c2h6: coleta.valores['c2h6'] ?? null,
  }),
  aplicaSe: aplicaSeDuval4,
  motivo: MOTIVO_DUVAL_4_NAO_APLICAVEL,
  paraPonto: duval4,
  gasAusente: gasAusenteDoDuval4,
});

const LEITURAS_DUVAL_5 = leiturasCondicionais({
  coletas: COLETAS_DE_GASES,
  gasesDa: (coleta) => ({
    ch4: coleta.valores['ch4'] ?? null,
    c2h4: coleta.valores['c2h4'] ?? null,
    c2h6: coleta.valores['c2h6'] ?? null,
  }),
  aplicaSe: aplicaSeDuval5,
  motivo: MOTIVO_DUVAL_5_NAO_APLICAVEL,
  paraPonto: duval5,
  gasAusente: gasAusenteDoDuval5,
});

const CODIGOS_DUVAL_4: readonly CodigoDeDuval4[] = LEITURAS_DUVAL_4.filter(
  (leitura) => leitura.tipo === 'plotada',
).map((leitura) => classificarDuval4(leitura.ponto));

const CODIGOS_DUVAL_5: readonly CodigoDeDuval5[] = LEITURAS_DUVAL_5.filter(
  (leitura) => leitura.tipo === 'plotada',
).map((leitura) => classificarDuval5(leitura.ponto));

/**
 * Motivo a exibir quando NENHUMA coleta licenciou a figura. Se faltou gás, o
 * motivo não é esse — quem explica é a contagem de incomputáveis.
 */
function motivoDaFiguraVazia(
  leituras: readonly LeituraDoTriangulo[],
  motivo: string,
): string | null {
  if (leituras.some((leitura) => leitura.tipo === 'plotada')) return null;
  return leituras.some((leitura) => leitura.tipo === 'naoAplicavel') ? motivo : null;
}

// DT fica de fora: é zona do triângulo de Duval (falha mista térmica/elétrica),
// não código de falha da IEC 60599, que só define PD, D1, D2, T1, T2 e T3.
// Listá-lo aqui faria a legenda atribuir à norma um código que ela não tem.
const CODIGOS_IEC: readonly CodigoDeFalha[] = (
  Object.keys(DESCRICAO_DE_DUVAL_1) as CodigoDeDuval1[]
)
  .filter((codigo) => codigo !== 'DT')
  .map((codigo) => ({ codigo, descricao: DESCRICAO_DE_DUVAL_1[codigo] }));

// A conclusão do triângulo 1 é o que a tabela normativa diz sobre a coleta mais
// recente que deu para computar, não um texto fixo.
const ULTIMO_CODIGO_DUVAL_1 = CODIGOS_DUVAL_1.at(-1);

const CONCLUSOES_DUVAL_1: readonly ConclusaoDeDiagnostico[] = [
  ULTIMO_CODIGO_DUVAL_1
    ? {
        metodo: 'Triangulo 1',
        codigo: ULTIMO_CODIGO_DUVAL_1,
        descricao: DESCRICAO_DE_DUVAL_1[ULTIMO_CODIGO_DUVAL_1],
      }
    : {
        metodo: 'Triangulo 1',
        codigo: '-',
        descricao: 'Sem coleta com os três gases do triângulo 1',
      },
];

/**
 * Conclusão de um triângulo condicional: o que a tabela normativa diz sobre a
 * coleta mais recente que a figura pôde ler. Sem leitura licenciada não sai
 * veredito — emitir código sobre figura não licenciada é justamente o erro
 * que esta tela cometia ao desenhar os três triângulos para toda amostra.
 */
function conclusaoDe<C extends string>(opcoes: {
  readonly metodo: string;
  readonly codigos: readonly C[];
  readonly descricoes: Record<C, string>;
  readonly semVeredito: string;
}): ConclusaoDeDiagnostico {
  const ultimo = opcoes.codigos.at(-1);
  return ultimo
    ? { metodo: opcoes.metodo, codigo: ultimo, descricao: opcoes.descricoes[ultimo] }
    : { metodo: opcoes.metodo, codigo: '—', descricao: opcoes.semVeredito };
}

export const INDICADORES: IndicadoresDeSaude = {
  equipamentoId: '58836',
  tag: 'TR-B05',

  fisicoQuimico: {
    conformidade: 'normal',
    veredito: 'APROVADO',
    tensaoNominal: '≤ 72,5 kV',
    coletas: [
      {
        data: DATAS_FQ[0],
        valores: {
          neutralizacao: 0.5,
          agua: 25,
          densidade: 0.9,
          fatorDePotencia: 2,
          rigidez: 49,
          tensaoInterfacial: 20,
          cor: 1,
        },
      },
      {
        data: DATAS_FQ[1],
        valores: {
          neutralizacao: 0.5,
          agua: 32,
          densidade: 0.9,
          fatorDePotencia: 2.5,
          rigidez: 49,
          tensaoInterfacial: 20,
          cor: 1,
        },
      },
      {
        data: DATAS_FQ[2],
        valores: {
          neutralizacao: 0.5,
          agua: 42,
          densidade: 0.9,
          fatorDePotencia: 2,
          rigidez: 60,
          tensaoInterfacial: 19.5,
          cor: 1,
        },
      },
      {
        data: DATAS_FQ[3],
        valores: {
          neutralizacao: 0.5,
          agua: 39,
          densidade: 0.9,
          fatorDePotencia: 2,
          rigidez: 48,
          tensaoInterfacial: 18.5,
          cor: 1,
        },
      },
      {
        data: DATAS_FQ[4],
        valores: {
          neutralizacao: 0.6,
          agua: 65,
          densidade: 0.9,
          fatorDePotencia: 2.5,
          rigidez: 37,
          tensaoInterfacial: 19,
          cor: 1,
        },
      },
    ],
    ensaios: [
      {
        chave: 'neutralizacao',
        nome: 'Índice de Neutralização (mg KOH/g óleo)',
        metodo: 'NBR 14248',
        limite: 'máx. 0,20',
        resultado: 0.6,
        classificacao: 'nao-conforme',
      },
      {
        chave: 'agua',
        nome: 'Teor de Água (ppm m/m) - Medido',
        metodo: 'NBR 10710',
        limite: 'máx. 40',
        resultado: 65,
        classificacao: 'normal',
      },
      {
        chave: 'densidade',
        nome: 'Densidade a 20/4°C (g/mL)',
        metodo: 'NBR 14065',
        limite: '—',
        resultado: 0.9,
        classificacao: 'nao-conforme',
      },
      {
        chave: 'fatorDePotencia',
        nome: 'Fator de Potência a 100°C (%)',
        metodo: 'NBR 12133',
        limite: 'máx. 20',
        resultado: 2.5,
        classificacao: 'alerta',
      },
      {
        chave: 'rigidez',
        nome: 'Rigidez Dielétrica - Calota (kV)',
        metodo: 'NBR/IEC 60156',
        limite: 'min. 40',
        resultado: 37,
        classificacao: 'normal',
      },
      {
        chave: 'tensaoInterfacial',
        nome: 'Tensão Interfacial (dina/cm)',
        metodo: 'NBR 6234',
        limite: 'min. 20',
        resultado: 19,
        classificacao: 'normal',
      },
    ],
    laudo:
      'De acordo com a norma ABNT NBR 10576/17, os resultados obtidos nesta análise indicam que o ' +
      'óleo mineral isolante encontra-se em boas condições físico-químicas. Sugere-se nova análise ' +
      'no prazo de 1 (um) ano.',
  },

  gasesDissolvidos: {
    conformidade: 'normal',
    veredito: 'APROVADO',
    datasDeColeta: ['16/09/2025', '27/11/2025'],
    coletas: COLETAS_DE_GASES,
    gases: [
      { chave: 'h2', nome: 'Hidrogênio', formula: 'H₂', resultados: [2028, 12] },
      { chave: 'o2', nome: 'Oxigênio', formula: 'O₂', resultados: [11256, 6930] },
      { chave: 'n2', nome: 'Nitrogênio', formula: 'N₂', resultados: [37451, 46943] },
      { chave: 'ch4', nome: 'Metano', formula: 'CH₄', resultados: [601, 3] },
      { chave: 'co', nome: 'Monóxido de Carbono', formula: 'CO', resultados: [1239, 730] },
      { chave: 'co2', nome: 'Dióxido de Carbono', formula: 'CO₂', resultados: [1228, 2788] },
      { chave: 'c2h4', nome: 'Etileno', formula: 'C₂H₄', resultados: [960, 15] },
      { chave: 'c2h6', nome: 'Etano', formula: 'C₂H₆', resultados: [74, 3] },
      { chave: 'c2h2', nome: 'Acetileno', formula: 'C₂H₂', resultados: [1297, null] },
      { chave: 'combustiveis', nome: 'Total de Gases Combustíveis', formula: '', resultados: [6199, 763] },
      { chave: 'total', nome: 'Total de Gases', formula: '', resultados: [56134, 57424] },
    ],
    laudo:
      'Baseado nos critérios adotados pelos Institutos Lactec, os resultados obtidos na análise são ' +
      'considerados normais. Sugere-se nova análise no prazo de 1 (um) ano.',
  },

  diagnostico: {
    triangulos: [
      {
        numero: 1,
        eixoEsquerdo: 'Methane CH₄ %',
        eixoDireito: 'Ethylene C₂H₄ %',
        eixoBase: 'Acetylene C₂H₂ %',
        zonas: zonasDuval1Com(CODIGOS_DUVAL_1),
        leituras: LEITURAS_DUVAL_1,
        naoAplicavel: null,
      },
      {
        numero: 4,
        eixoEsquerdo: 'Hydrogen H₂ %',
        eixoDireito: 'Methane CH₄ %',
        eixoBase: 'Ethane C₂H₆ %',
        zonas: zonasDuval4Com(CODIGOS_DUVAL_4),
        leituras: LEITURAS_DUVAL_4,
        naoAplicavel: motivoDaFiguraVazia(LEITURAS_DUVAL_4, MOTIVO_DUVAL_4_NAO_APLICAVEL),
      },
      {
        numero: 5,
        eixoEsquerdo: 'Methane CH₄ %',
        eixoDireito: 'Ethylene C₂H₄ %',
        eixoBase: 'Ethane C₂H₆ %',
        zonas: zonasDuval5Com(CODIGOS_DUVAL_5),
        leituras: LEITURAS_DUVAL_5,
        naoAplicavel: motivoDaFiguraVazia(LEITURAS_DUVAL_5, MOTIVO_DUVAL_5_NAO_APLICAVEL),
      },
    ],
    pentagono: { zona: 'T2', x: 0, y: 0 },
    ieee: {
      serie: [12, 18, 40, 260, 620, 540, 470, 500, 430, 280, 150, 90, 60, 45, 38],
      alarme: 600,
      atencao: 400,
    },
    // A legenda sai do mesmo dicionário que o diagnóstico usa: antes dizia que
    // T1/T2 eram "baixa/alta energia", quando T1/T2/T3 são faixas de
    // temperatura (<300 °C, 300-700 °C, >700 °C). Energia é D1/D2.
    codigosIec: CODIGOS_IEC,
    nbr7274: [
      { gas: 'H₂', valor: 2028, limite: 1500 },
      { gas: 'CH₄', valor: 601, limite: 1000 },
      { gas: 'C₂H₄', valor: 960, limite: 800 },
      { gas: 'C₂H₂', valor: 400, limite: 700 },
      { gas: 'CO₂', valor: 228, limite: 900 },
    ],
    rogers: [
      { linha: 'CH₄', coluna: 'CH₄', resultado: 'descarga' },
      { linha: 'CH₄', coluna: 'H₂', resultado: 'descarga' },
      { linha: 'CH₄', coluna: 'C₂H₂', resultado: 'indeterminado' },
      { linha: 'C₂H₂', coluna: 'CH₄', resultado: 'descarga' },
      { linha: 'C₂H₂', coluna: 'H₂', resultado: 'termico' },
      { linha: 'C₂H₂', coluna: 'C₂H₂', resultado: 'indeterminado' },
      { linha: 'CO₂', coluna: 'CH₄', resultado: 'indeterminado' },
      { linha: 'CO₂', coluna: 'H₂', resultado: 'termico' },
      { linha: 'CO₂', coluna: 'C₂H₂', resultado: 'descarga' },
    ],
    conclusoes: [
      ...CONCLUSOES_DUVAL_1,
      // Agora saem do classificador — mas só quando o triângulo 1 licencia a
      // figura. Sem licença não há veredito: a geometria responderia de
      // qualquer jeito, e é essa resposta automática que a norma proíbe ler.
      conclusaoDe({
        metodo: 'Triangulo 4',
        codigos: CODIGOS_DUVAL_4,
        descricoes: DESCRICAO_DE_DUVAL_4,
        semVeredito:
          motivoDaFiguraVazia(LEITURAS_DUVAL_4, MOTIVO_DUVAL_4_NAO_APLICAVEL) ??
          'Sem coleta com os três gases do triângulo 4',
      }),
      conclusaoDe({
        metodo: 'Triangulo 5',
        codigos: CODIGOS_DUVAL_5,
        descricoes: DESCRICAO_DE_DUVAL_5,
        semVeredito:
          motivoDaFiguraVazia(LEITURAS_DUVAL_5, MOTIVO_DUVAL_5_NAO_APLICAVEL) ??
          'Sem coleta com os três gases do triângulo 5',
      }),
    ],
  },
};
