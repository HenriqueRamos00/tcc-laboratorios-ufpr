// Gera a massa de teste de cromatografia do frontend a partir do dado real.
//
// Entrada: os dois arquivos JSON Lines do adaptador de amostra embutida do backend,
// que sao a transcrição das views do AutoLAB. Saida:
// `frontend/src/app/model/measured-gas-sample.spec-util.ts`.
//
// Como regerar, da raiz do repositorio. Nada roda no host, como manda o CLAUDE.md:
//
//   docker run --rm -v "$PWD:/w" -w /w node:22-alpine \
//     node tools/gerar-amostra-de-cromatografia.mjs
//
// Era um script Python, e Python rodado na maquina do desenvolvedor contraria a regra
// do projeto tanto quanto `node` ou `dotnet` soltos. Em Node ele roda no mesmo
// contentor que o resto do frontend ja usa, sem exigir nada instalado.
//
// O script RECUSA escrever quando o totalizador publicado de um protocolo nao bate
// exatamente com a soma dos gases daquele protocolo. A igualdade e exata de proposito:
// tolerancia promoveria um gas medido a totalizador.

import { readFileSync, writeFileSync } from 'node:fs';

const GAS_KEY_BY_VIEW_NAME = {
  'Hidrogênio': 'h2',
  'Oxigênio': 'o2',
  'Nitrogênio': 'n2',
  'Metano': 'ch4',
  'Monóxido de Carbono': 'co',
  'Dióxido de Carbono': 'co2',
  'Etileno': 'c2h4',
  'Etano': 'c2h6',
  'Acetileno': 'c2h2',
};

const TOTALIZER_KEY_BY_VIEW_NAME = {
  'Total de Gases Combustíveis': 'combustibleGases',
  'Total de Gases': 'allGases',
};

const GAS_ORDER = ['h2', 'o2', 'n2', 'ch4', 'co', 'co2', 'c2h4', 'c2h6', 'c2h2'];
const COMBUSTIBLE_GAS_KEYS = ['h2', 'ch4', 'co', 'c2h4', 'c2h6', 'c2h2'];

const SOURCE_FILE_BY_PROVENANCE = {
  medida: 'LabTestSample.jsonl',
  demonstracao: 'DemonstrationSeries.jsonl',
};

const ADAPTER_DIRECTORY = 'backend-portal-web/PortalHexagonalApi/Portal.Adapters/LabResults';
const OUTPUT = 'frontend/src/app/model/measured-gas-sample.spec-util.ts';

function readRows() {
  const rows = [];

  for (const [provenance, fileName] of Object.entries(SOURCE_FILE_BY_PROVENANCE)) {
    const content = readFileSync(`${ADAPTER_DIRECTORY}/${fileName}`, 'utf8');

    for (const line of content.split('\n')) {
      if (line.trim()) rows.push({ provenance, row: JSON.parse(line) });
    }
  }

  return rows;
}

function groupByProtocol() {
  const groups = new Map();

  for (const { provenance, row } of readRows()) {
    const protocol = row.protocoloLactec;

    if (!groups.has(protocol)) {
      groups.set(protocol, {
        protocoloLactec: protocol,
        identificacao: row.identificacao,
        serialNumber: row.numSerie,
        companyName: row.empresaDoTransformador,
        analysisDate: row.dataFinalAnalise.slice(0, 10),
        provenance,
        results: {},
      });
    }

    groups.get(protocol).results[row.name] = row.resultado;
  }

  return groups;
}

/** 'ND' entra como zero medido; celula vazia continua ausente. */
function partsPerMillion(raw) {
  if (raw === null || raw === undefined) return null;

  return raw === 'ND' ? 0 : Number.parseInt(raw, 10);
}

/**
 * A forma que a API entrega no corpo: numero, 'ND' ou null. A coluna da view e texto,
 * o corpo da API nao: resultado numerico viaja como numero, senao a tela compara texto
 * com texto e a ordem do grafico sai alfabetica.
 */
function measuredValue(raw) {
  if (raw === null || raw === undefined || raw === 'ND') return raw ?? null;

  return Number.parseInt(raw, 10);
}

function chromatographySamples() {
  const samples = [];

  for (const group of groupByProtocol().values()) {
    const names = new Set(Object.keys(group.results));
    const temTodosOsGases = Object.keys(GAS_KEY_BY_VIEW_NAME).every((nome) => names.has(nome));
    const temOsTotalizadores = Object.keys(TOTALIZER_KEY_BY_VIEW_NAME).every((nome) => names.has(nome));

    if (!temTodosOsGases || !temOsTotalizadores) continue;

    const rawResults = {};
    const gases = {};
    const totalizers = {};

    for (const [viewName, key] of Object.entries(GAS_KEY_BY_VIEW_NAME)) {
      rawResults[key] = measuredValue(group.results[viewName]);
      gases[key] = partsPerMillion(group.results[viewName]);
    }

    for (const [viewName, key] of Object.entries(TOTALIZER_KEY_BY_VIEW_NAME)) {
      rawResults[key] = measuredValue(group.results[viewName]);
      totalizers[key] = partsPerMillion(group.results[viewName]);
    }

    samples.push({ ...group, rawResults, gases, totalizers });
  }

  samples.sort((um, outro) =>
    um.analysisDate === outro.analysisDate
      ? um.protocoloLactec.localeCompare(outro.protocoloLactec)
      : um.analysisDate.localeCompare(outro.analysisDate),
  );

  return samples;
}

function refuseWhenTotalizerDoesNotMatch(samples) {
  const problems = [];

  for (const sample of samples) {
    const allGases = GAS_ORDER.reduce((total, key) => total + sample.gases[key], 0);
    const combustible = COMBUSTIBLE_GAS_KEYS.reduce((total, key) => total + sample.gases[key], 0);

    if (allGases !== sample.totalizers.allGases) {
      problems.push(
        `${sample.protocoloLactec}: soma dos gases ${allGases} contra ` +
          `Total de Gases publicado ${sample.totalizers.allGases}`,
      );
    }

    if (combustible !== sample.totalizers.combustibleGases) {
      problems.push(
        `${sample.protocoloLactec}: soma dos combustiveis ${combustible} contra ` +
          `Total de Gases Combustiveis publicado ${sample.totalizers.combustibleGases}`,
      );
    }
  }

  if (problems.length) {
    for (const problem of problems) console.error(`divergencia: ${problem}`);
    process.exit(1);
  }
}

function asTypeScriptLiteral(value) {
  if (value === null || value === undefined) return 'null';

  return typeof value === 'string' ? `'${value}'` : String(value);
}

const HEADER = `// Massa de teste de cromatografia derivada do dado REAL das views do AutoLAB.
//
// ARQUIVO GERADO. Nao edite a mao: regere com
// \`tools/gerar-amostra-de-cromatografia.mjs\`, que le os dois arquivos JSON
// Lines do adaptador de amostra embutida do backend e recusa gravar quando o
// totalizador publicado de um protocolo não bate exatamente com a soma dos nove
// gases daquele protocolo.
//
// Fonte, por procedência - a distinção é a mesma que o backend faz em
// \`ReadMeasuredSample()\`, e existe porque teste que afirma sobre número medido
// não pode ler número escrito à mão:
//
//   'medida'        LabTestSample.jsonl, transcrição das views.
//   'demonstracao'  DemonstrationSeries.jsonl, escrito à mão para um
//                   equipamento ter mais de um laudo.
//
// NÃO é código de produção: nada no aplicativo importa este arquivo. Ele existe
// para os testes afirmarem sobre número medido, e não sobre número escolhido por
// conveniência de quem escreve o teste.
//
// O resultado da view tem exatamente três formas e as três significam coisas
// diferentes. \`rawResults\` guarda a forma que chegou e \`gasesInPartsPerMillion\`
// guarda o número que entra na conta:
//
//   número  o ensaio foi feito e deu aquilo          -> o próprio número
//   'ND'    o ensaio foi feito e nada foi detectado  -> zero medido
//   null    o ensaio não foi realizado               -> sem medição
//
// Colapsar 'ND' em null faria o laudo com metano, etileno e acetileno todos em
// 'ND' sair como falta de dado, quando o fato é total zero: o triângulo é
// genuinamente incomputável porque nada foi detectado, não porque ninguém mediu.

import type { GasesDoDuval1 } from './duval-1';
import type { GasesDoDuval4 } from './duval-4';
import type { GasesDoDuval5 } from './duval-5';
import type { AnalysisReading, DissolvedGas, DissolvedGasBlock, MeasuredValue } from './health';

/** Os nove gases da cromatografia, na chave que o bloco de gases dissolvidos usa. */
export type GasKey = 'h2' | 'o2' | 'n2' | 'ch4' | 'co' | 'co2' | 'c2h4' | 'c2h6' | 'c2h2';

/** Os dois totalizadores publicados, que NÃO são gases medidos. */
export type TotalizerKey = 'combustibleGases' | 'allGases';

export type SampleProvenance = 'medida' | 'demonstracao';

export interface ChromatographySample {
  readonly protocoloLactec: string;
  readonly identificacao: string;
  /** Pode não ser numérico: existe \`111.594/1\` na amostra real. */
  readonly serialNumber: string;
  readonly companyName: string;
  readonly analysisDate: string;
  readonly provenance: SampleProvenance;
  readonly rawResults: Readonly<Record<GasKey | TotalizerKey, MeasuredValue>>;
  readonly gasesInPartsPerMillion: Readonly<Record<GasKey, number>>;
  readonly publishedTotalizers: Readonly<Record<TotalizerKey, number>>;
}

/** Ordem de exibição dos gases no laudo impresso. */
export const GAS_KEYS: readonly GasKey[] = [
  'h2',
  'o2',
  'n2',
  'ch4',
  'co',
  'co2',
  'c2h4',
  'c2h6',
  'c2h2',
];

/** Os seis gases que o Total de Gases Combustíveis soma. */
export const COMBUSTIBLE_GAS_KEYS: readonly GasKey[] = ['h2', 'ch4', 'co', 'c2h4', 'c2h6', 'c2h2'];

export const GAS_NAME_BY_KEY: Readonly<Record<GasKey, string>> = {
  h2: 'Hidrogênio',
  o2: 'Oxigênio',
  n2: 'Nitrogênio',
  ch4: 'Metano',
  co: 'Monóxido de Carbono',
  co2: 'Dióxido de Carbono',
  c2h4: 'Etileno',
  c2h6: 'Etano',
  c2h2: 'Acetileno',
};

export const GAS_FORMULA_BY_KEY: Readonly<Record<GasKey, string>> = {
  h2: 'H₂',
  o2: 'O₂',
  n2: 'N₂',
  ch4: 'CH₄',
  co: 'CO',
  co2: 'CO₂',
  c2h4: 'C₂H₄',
  c2h6: 'C₂H₆',
  c2h2: 'C₂H₂',
};
`;

const FOOTER = `
/**
 * Os gases de cada triangulo, lidos da coleta. Existem aqui, e não soltos em
 * cada teste, porque troca de eixo é invisível ao compilador: \`ch4\` e \`c2h4\`
 * tem o mesmo tipo, e um extrator trocado passa batido. Quem pega a troca são as
 * coordenadas afirmadas nos testes, uma por uma.
 */
export function duval1GasesOf(sample: ChromatographySample): GasesDoDuval1 {
  const gases = sample.gasesInPartsPerMillion;
  return { ch4: gases.ch4, c2h4: gases.c2h4, c2h2: gases.c2h2 };
}

export function duval4GasesOf(sample: ChromatographySample): GasesDoDuval4 {
  const gases = sample.gasesInPartsPerMillion;
  return { h2: gases.h2, ch4: gases.ch4, c2h6: gases.c2h6 };
}

export function duval5GasesOf(sample: ChromatographySample): GasesDoDuval5 {
  const gases = sample.gasesInPartsPerMillion;
  return { ch4: gases.ch4, c2h4: gases.c2h4, c2h6: gases.c2h6 };
}

/** Somente os protocolos transcritos das views, sem os escritos à mão. */
export const MEASURED_CHROMATOGRAPHY_SAMPLES: readonly ChromatographySample[] =
  CHROMATOGRAPHY_SAMPLES.filter((sample) => sample.provenance === 'medida');

/**
 * O protocolo pedido, ou exceção. Devolver \`undefined\` deixaria o teste afirmar
 * sobre nada e passar: protocolo que sumiu da amostra tem que acender a luz na
 * hora, com o nome do que faltou.
 */
export function chromatographySampleOf(protocoloLactec: string): ChromatographySample {
  const found = CHROMATOGRAPHY_SAMPLES.find((sample) => sample.protocoloLactec === protocoloLactec);
  if (!found) throw new Error(\`protocolo \${protocoloLactec} não está na massa de cromatografia\`);
  return found;
}

/** Os laudos de um equipamento, em ordem cronológica. */
export function chromatographySamplesOfEquipment(
  serialNumber: string,
): readonly ChromatographySample[] {
  return CHROMATOGRAPHY_SAMPLES.filter((sample) => sample.serialNumber === serialNumber);
}

/**
 * O bloco de gases dissolvidos como o fio o entrega, montado a partir dos
 * protocolos pedidos. \`conformity\` e \`verdict\` saem nulos porque o nucleo
 * normativo não roda no backend: o AutoLAB entrega o valor medido, não o
 * parecer, e nulo aqui é ensaio ainda sem parecer.
 */
export function dissolvedGasBlockOf(
  samples: readonly ChromatographySample[],
): DissolvedGasBlock {
  const analyses: readonly AnalysisReading[] = samples.map((sample) => ({
    date: sample.analysisDate,
    values: Object.fromEntries(
      GAS_KEYS.map((key) => [key, sample.gasesInPartsPerMillion[key]]),
    ) as Readonly<Record<string, number | null>>,
  }));
  const gases: readonly DissolvedGas[] = GAS_KEYS.map((key) => ({
    key,
    name: GAS_NAME_BY_KEY[key],
    formula: GAS_FORMULA_BY_KEY[key],
    results: samples.map((sample) => sample.rawResults[key]),
  }));
  return {
    conformity: null,
    verdict: null,
    analysisDates: samples.map((sample) => sample.analysisDate),
    analyses,
    gases,
    report: {
      title: 'Laudo da análise cromatográfica',
      paragraphs: [],
    },
  };
}
`;

function render(samples) {
  const partes = [HEADER, '\nexport const CHROMATOGRAPHY_SAMPLES: readonly ChromatographySample[] = [\n'];

  for (const sample of samples) {
    partes.push('  {\n');
    partes.push(`    protocoloLactec: '${sample.protocoloLactec}',\n`);
    partes.push(`    identificacao: '${sample.identificacao}',\n`);
    partes.push(`    serialNumber: '${sample.serialNumber}',\n`);
    partes.push(`    companyName: '${sample.companyName}',\n`);
    partes.push(`    analysisDate: '${sample.analysisDate}',\n`);
    partes.push(`    provenance: '${sample.provenance}',\n`);
    partes.push('    rawResults: {\n');

    for (const key of [...GAS_ORDER, 'combustibleGases', 'allGases']) {
      partes.push(`      ${key}: ${asTypeScriptLiteral(sample.rawResults[key])},\n`);
    }

    partes.push('    },\n');
    partes.push('    gasesInPartsPerMillion: {\n');

    for (const key of GAS_ORDER) partes.push(`      ${key}: ${sample.gases[key]},\n`);

    partes.push('    },\n');
    partes.push('    publishedTotalizers: {\n');

    for (const key of ['combustibleGases', 'allGases']) {
      partes.push(`      ${key}: ${sample.totalizers[key]},\n`);
    }

    partes.push('    },\n');
    partes.push('  },\n');
  }

  partes.push('];\n', FOOTER);

  return partes.join('');
}

const samples = chromatographySamples();

refuseWhenTotalizerDoesNotMatch(samples);
writeFileSync(OUTPUT, render(samples), 'utf8');
console.log(`${samples.length} protocolos de cromatografia gravados em ${OUTPUT}`);

for (const sample of samples) {
  console.log(`  ${sample.analysisDate}  ${sample.protocoloLactec}  ${sample.provenance}`);
}
