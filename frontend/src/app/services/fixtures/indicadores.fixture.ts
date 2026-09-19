// Resultados, datas e traçado dos gráficos vêm das telas aprovadas.
//
// ATENÇÃO PARA A REVISÃO: as zonas do triângulo de Duval 1 seguem as regras
// publicadas. As dos triângulos 4 e 5 foram desenhadas pelo layout da tela e
// precisam ser conferidas com o laboratório antes de irem para produção.

import type { IndicadoresDeSaude, PontoTernario, ZonaTernaria } from '@/app/model/health';

const DATAS_FQ = ['20/04/2021', '20/05/2022', '19/09/2023', '26/04/2024', '26/10/2025'];

function zona(codigo: string, vertices: readonly PontoTernario[], destacada = false): ZonaTernaria {
  return { codigo, vertices, destacada };
}

const ponto = (a: number, b: number, c: number): PontoTernario => ({ a, b, c });

// Duval 1: a = %CH4, b = %C2H4, c = %C2H2.
const ZONAS_DUVAL_1: readonly ZonaTernaria[] = [
  zona('PD', [ponto(98, 0, 2), ponto(100, 0, 0), ponto(98, 2, 0)]),
  zona('T1', [ponto(98, 2, 0), ponto(98, 0, 2), ponto(96, 0, 4), ponto(76, 20, 4), ponto(80, 20, 0)], true),
  zona('T2', [ponto(80, 20, 0), ponto(76, 20, 4), ponto(46, 50, 4), ponto(50, 50, 0)], true),
  zona('T3', [ponto(50, 50, 0), ponto(46, 50, 4), ponto(35, 50, 15), ponto(0, 85, 15), ponto(0, 100, 0)]),
  zona('D1', [ponto(87, 0, 13), ponto(64, 23, 13), ponto(0, 23, 77), ponto(0, 0, 100)]),
  zona('D2', [ponto(64, 23, 13), ponto(47, 40, 13), ponto(31, 40, 29), ponto(21, 50, 29), ponto(0, 50, 50), ponto(0, 23, 77)]),
  zona(
    'DT',
    [
      ponto(96, 0, 4),
      ponto(76, 20, 4),
      ponto(46, 50, 4),
      ponto(35, 50, 15),
      ponto(21, 50, 29),
      ponto(31, 40, 29),
      ponto(47, 40, 13),
      ponto(64, 23, 13),
      ponto(87, 0, 13),
    ],
    true,
  ),
];

// Duval 4: a = %H2, b = %CH4, c = %C2H6.
const ZONAS_DUVAL_4: readonly ZonaTernaria[] = [
  zona('PD', [ponto(100, 0, 0), ponto(85, 15, 0), ponto(85, 0, 15)]),
  zona('ND', [ponto(85, 15, 0), ponto(85, 0, 15), ponto(36, 0, 64), ponto(36, 49, 15)], true),
  zona('S', [ponto(36, 49, 15), ponto(36, 0, 64), ponto(0, 0, 100), ponto(0, 64, 36)]),
  zona('C', [ponto(0, 64, 36), ponto(0, 100, 0), ponto(36, 64, 0), ponto(36, 49, 15)]),
  zona('O', [ponto(36, 64, 0), ponto(36, 49, 15), ponto(85, 15, 0)]),
];

// Duval 5: a = %CH4, b = %C2H4, c = %C2H6.
const ZONAS_DUVAL_5: readonly ZonaTernaria[] = [
  zona('PD', [ponto(100, 0, 0), ponto(90, 10, 0), ponto(90, 0, 10)]),
  zona('O', [ponto(90, 10, 0), ponto(90, 0, 10), ponto(54, 0, 46), ponto(54, 36, 10)], true),
  zona('S', [ponto(54, 36, 10), ponto(54, 0, 46), ponto(20, 0, 80), ponto(20, 60, 20)], true),
  zona('ND', [ponto(20, 60, 20), ponto(20, 0, 80), ponto(0, 0, 100), ponto(0, 60, 40)], true),
  zona('C', [ponto(54, 36, 10), ponto(20, 60, 20), ponto(0, 60, 40), ponto(0, 85, 15), ponto(46, 46, 8)]),
  zona('T2-H', [ponto(90, 10, 0), ponto(54, 36, 10), ponto(46, 46, 8), ponto(60, 40, 0)]),
  zona('T3-H', [ponto(0, 85, 15), ponto(0, 100, 0), ponto(60, 40, 0), ponto(46, 46, 8)]),
];

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
    coletas: [
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
    ],
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
        zonas: ZONAS_DUVAL_1,
        pontos: [ponto(88, 10, 2), ponto(84, 14, 2), ponto(74, 23, 3), ponto(62, 34, 4)],
      },
      {
        numero: 4,
        eixoEsquerdo: 'Hydrogen H₂ %',
        eixoDireito: 'Methane CH₄ %',
        eixoBase: 'Ethane C₂H₆ %',
        zonas: ZONAS_DUVAL_4,
        pontos: [ponto(44, 12, 44)],
      },
      {
        numero: 5,
        eixoEsquerdo: 'Methane CH₄ %',
        eixoDireito: 'Ethylene C₂H₄ %',
        eixoBase: 'Ethane C₂H₆ %',
        zonas: ZONAS_DUVAL_5,
        pontos: [ponto(46, 24, 30), ponto(40, 30, 30), ponto(52, 18, 30)],
      },
    ],
    pentagono: { zona: 'T2', x: 0, y: 0 },
    ieee: {
      serie: [12, 18, 40, 260, 620, 540, 470, 500, 430, 280, 150, 90, 60, 45, 38],
      alarme: 600,
      atencao: 400,
    },
    codigosIec: [
      { codigo: 'T1', descricao: 'Falha térmica de baixa energia' },
      { codigo: 'T2', descricao: 'Falha térmica de alta energia' },
      { codigo: 'T3', descricao: 'Falha térmica' },
      { codigo: 'D1', descricao: 'Descarga elétrica de baixa energia' },
      { codigo: 'PD', descricao: 'Descarga Parcial' },
    ],
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
      { metodo: 'Triangulo 1', codigo: 'TD', descricao: 'Pontos quentes' },
      { metodo: 'Triangulo 4', codigo: 'TD', descricao: 'Pontos quentes' },
      { metodo: 'Triangulo 5', codigo: 'TD', descricao: 'Pontos quentes' },
    ],
  },
};
