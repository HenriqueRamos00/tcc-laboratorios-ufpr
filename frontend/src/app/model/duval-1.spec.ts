import { classificarDuval1, duval1, ZONAS_DUVAL_1, type CodigoDeDuval1 } from './duval-1';
import type { PontoTernario } from './health';

// Este arquivo é o oráculo dos polígonos: regenera ZONAS_DUVAL_1 a partir da
// tabela de desigualdades e falha se alguém editar um vértice na mão. A
// geometria vive AQUI, e não no código de produção, porque nada no aplicativo
// precisa dela em tempo de execução — a classificação é sempre pela tabela.
//
// Trabalhamos no plano (b, c) = (%C₂H₄, %C₂H₂), com a = 100 − b − c. Todas as
// fronteiras da tabela são retas b = k ou c = k, mais a diagonal de PD
// (b + c = 2) e a hipotenusa (b + c = 100). Por isso dá para cortar o simplex
// numa grade, classificar cada célula pelo centroide e colar as células de
// mesmo código cancelando as arestas internas.

type XY = readonly [number, number];

const CORTES_B = [0, 20, 23, 40, 50, 100];
const CORTES_C = [0, 4, 13, 15, 29, 100];

/** Sutherland-Hodgman: mantém o lado onde `dentro` é >= 0. */
function cortar(poligono: readonly XY[], dentro: (p: XY) => number): XY[] {
  const saida: XY[] = [];
  for (let i = 0; i < poligono.length; i++) {
    const p = poligono[i];
    const q = poligono[(i + 1) % poligono.length];
    const dp = dentro(p);
    const dq = dentro(q);
    if (dp >= 0) saida.push(p);
    if ((dp > 0 && dq < 0) || (dp < 0 && dq > 0)) {
      const t = dp / (dp - dq);
      saida.push([p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])]);
    }
  }
  return saida;
}

function areaAssinada(anel: readonly XY[]): number {
  let soma = 0;
  for (let i = 0; i < anel.length; i++) {
    const p = anel[i];
    const q = anel[(i + 1) % anel.length];
    soma += p[0] * q[1] - q[0] * p[1];
  }
  return soma / 2;
}

const chave = (p: XY): string => `${Math.round(p[0] * 1e6)}:${Math.round(p[1] * 1e6)}`;

/** Cola peças adjacentes cancelando as arestas que aparecem nos dois sentidos. */
function colar(pecas: readonly (readonly XY[])[]): XY[][] {
  const arestas = new Map<string, XY[]>();
  for (const peca of pecas) {
    const anel = areaAssinada(peca) < 0 ? [...peca].reverse() : [...peca];
    for (let i = 0; i < anel.length; i++) {
      const p = anel[i];
      const q = anel[(i + 1) % anel.length];
      const inversa = `${chave(q)}|${chave(p)}`;
      if (arestas.has(inversa)) arestas.delete(inversa);
      else arestas.set(`${chave(p)}|${chave(q)}`, [p, q]);
    }
  }
  const seguinte = new Map<string, XY[]>();
  for (const [, [p, q]] of arestas) {
    const k = chave(p);
    seguinte.set(k, [...(seguinte.get(k) ?? []), q]);
  }
  const aneis: XY[][] = [];
  const pontoDe = new Map<string, XY>();
  for (const [, [p, q]] of arestas) {
    pontoDe.set(chave(p), p);
    pontoDe.set(chave(q), q);
  }
  while (seguinte.size) {
    const inicio = seguinte.keys().next().value as string;
    const anel: XY[] = [pontoDe.get(inicio)!];
    let atual = inicio;
    for (;;) {
      const destinos = seguinte.get(atual);
      if (!destinos?.length) throw new Error('anel aberto ao colar as peças');
      const q = destinos.shift()!;
      if (!destinos.length) seguinte.delete(atual);
      if (chave(q) === inicio) break;
      anel.push(q);
      atual = chave(q);
    }
    aneis.push(anel);
  }
  return aneis;
}

/** Tira vértices colineares para o anel sair com a mesma forma do literal. */
function semColineares(anel: readonly XY[]): XY[] {
  return anel.filter((q, i) => {
    const p = anel[(i - 1 + anel.length) % anel.length];
    const r = anel[(i + 1) % anel.length];
    return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0]) !== 0;
  });
}

/** Mesma forma, independente de por onde o anel começa e do sentido. */
function normalizar(anel: readonly XY[]): string {
  const orientado = areaAssinada(anel) < 0 ? [...anel].reverse() : [...anel];
  const chaves = orientado.map(chave);
  const inicio = chaves.indexOf([...chaves].sort()[0]);
  return [...chaves.slice(inicio), ...chaves.slice(0, inicio)].join(' ');
}

function gerarZonas(): Map<CodigoDeDuval1, XY[]> {
  const pecas = new Map<CodigoDeDuval1, XY[][]>();
  for (let i = 0; i < CORTES_B.length - 1; i++) {
    for (let j = 0; j < CORTES_C.length - 1; j++) {
      const celula: XY[] = [
        [CORTES_B[i], CORTES_C[j]],
        [CORTES_B[i + 1], CORTES_C[j]],
        [CORTES_B[i + 1], CORTES_C[j + 1]],
        [CORTES_B[i], CORTES_C[j + 1]],
      ];
      const noSimplex = cortar(celula, (p) => 100 - p[0] - p[1]);
      if (noSimplex.length < 3) continue;
      // A diagonal de PD corta células; separa os dois lados dela.
      for (const lado of [(p: XY) => 2 - p[0] - p[1], (p: XY) => p[0] + p[1] - 2]) {
        const peca = cortar(noSimplex, lado);
        if (peca.length < 3 || Math.abs(areaAssinada(peca)) < 1e-9) continue;
        const cx = peca.reduce((s, p) => s + p[0], 0) / peca.length;
        const cy = peca.reduce((s, p) => s + p[1], 0) / peca.length;
        const codigo = classificarDuval1({ a: 100 - cx - cy, b: cx, c: cy });
        pecas.set(codigo, [...(pecas.get(codigo) ?? []), peca]);
      }
    }
  }
  const zonas = new Map<CodigoDeDuval1, XY[]>();
  for (const [codigo, lista] of pecas) {
    const aneis = colar(lista);
    if (aneis.length !== 1) throw new Error(`${codigo} virou ${aneis.length} anéis`);
    zonas.set(codigo, semColineares(aneis[0]));
  }
  return zonas;
}

const comoXY = (vertices: readonly PontoTernario[]): XY[] => vertices.map((v) => [v.b, v.c]);

describe('Triângulo de Duval 1', () => {
  const geradas = gerarZonas();

  it('cobre o triângulo inteiro: sem buraco e sem sobreposição', () => {
    // O simplex em (b, c) é o triângulo retângulo de catetos 100: área 5000.
    // Se houvesse buraco a soma daria menos; se houvesse sobreposição, mais.
    const total = [...geradas.values()].reduce((s, anel) => s + Math.abs(areaAssinada(anel)), 0);
    expect(total).toBeCloseTo(5000, 6);
  });

  it('desenha exatamente as zonas que a tabela normativa define', () => {
    const codigosDesenhados = ZONAS_DUVAL_1.map((zona) => zona.codigo).sort();
    expect([...geradas.keys()].sort() as string[]).toEqual(codigosDesenhados);
    for (const zona of ZONAS_DUVAL_1) {
      const esperada = geradas.get(zona.codigo as CodigoDeDuval1);
      expect(esperada).withContext(`zona ${zona.codigo} não foi gerada`).toBeDefined();
      expect(normalizar(comoXY(zona.vertices)))
        .withContext(`vértices de ${zona.codigo} divergem da tabela`)
        .toBe(normalizar(esperada!));
    }
  });

  it('classifica todo ponto do triângulo, inclusive na cunha que faltava', () => {
    // A versão anterior deixava %C₂H₄ > 50 ∧ %C₂H₂ > 15 sem zona nenhuma.
    // Varredura com passo fracionário para não cair em cima de fronteira.
    let amostras = 0;
    for (let b = 0.37; b < 100; b += 0.61) {
      for (let c = 0.23; b + c < 100; c += 0.53) {
        expect(classificarDuval1({ a: 100 - b - c, b, c })).toBeTruthy();
        amostras++;
      }
    }
    expect(amostras).toBeGreaterThan(10000);
  });

  it('não computa a coleta sem os três gases, e não a confunde com zero', () => {
    expect(duval1({ ch4: 250, c2h4: 380, c2h2: null })).toBeNull();
    expect(duval1({ ch4: 0, c2h4: 0, c2h2: 0 })).toBeNull();
    const ponto = duval1({ ch4: 250, c2h4: 380, c2h2: 2 });
    expect(ponto).not.toBeNull();
    expect(ponto!.a + ponto!.b + ponto!.c).toBeCloseTo(100, 9);
  });

  it('respeita as desigualdades meia-abertas nas fronteiras da tabela', () => {
    // C₂H₄ = 40 com C₂H₂ = 20 é DT, não D2: a regra é `< 40`, não `<= 40`.
    expect(classificarDuval1({ a: 40, b: 40, c: 20 })).toBe('DT');
    expect(classificarDuval1({ a: 41, b: 39, c: 20 })).toBe('D2');
    expect(classificarDuval1({ a: 98, b: 2, c: 0 })).toBe('PD');
    expect(classificarDuval1({ a: 97, b: 3, c: 0 })).toBe('T1');
    expect(classificarDuval1({ a: 30, b: 60, c: 10 })).toBe('T3');
  });
});
