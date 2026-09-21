// Gerador de polígonos a partir de uma tabela de desigualdades — oráculo dos
// testes dos triângulos 4 e 5. NÃO é código de produção: nada no aplicativo
// importa este arquivo, porque a classificação é sempre pela tabela, nunca
// por ponto-em-polígono.
//
// É o mesmo algoritmo que vive dentro de `duval-1.spec.ts` (cortar o simplex
// numa grade, classificar cada célula pelo centroide, colar as células de
// mesmo código cancelando as arestas internas), com duas generalizações que o
// triângulo 1 não precisava e que por isso não foram lá mexer:
//
//  1. CORTES DIAGONAIS. No triângulo 4 há fronteiras em %H₂, que é o gás do
//     vértice de cima — no plano (b, c) elas viram retas b + c = k. O
//     triângulo 1 só tinha uma diagonal (a de PD) e a tratava à mão.
//  2. ZONAS COM MAIS DE UM ANEL. A zona O do triângulo 5 é publicada em duas
//     regiões disjuntas, então o gerador devolve uma lista de anéis por
//     código em vez de exigir exatamente um.
//
// Trabalhamos no plano (b, c), com a = 100 − b − c.

/** Ponto no plano (b, c). */
export type XY = readonly [number, number];

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

export function areaAssinada(anel: readonly XY[]): number {
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
  const pontoDe = new Map<string, XY>();
  for (const [, [p, q]] of arestas) {
    seguinte.set(chave(p), [...(seguinte.get(chave(p)) ?? []), q]);
    pontoDe.set(chave(p), p);
    pontoDe.set(chave(q), q);
  }
  const aneis: XY[][] = [];
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
export function normalizar(anel: readonly XY[]): string {
  const orientado = areaAssinada(anel) < 0 ? [...anel].reverse() : [...anel];
  const chaves = orientado.map(chave);
  const inicio = chaves.indexOf([...chaves].sort()[0]);
  return [...chaves.slice(inicio), ...chaves.slice(0, inicio)].join(' ');
}

export interface Particao<Codigo extends string> {
  readonly cortesB: readonly number[];
  readonly cortesC: readonly number[];
  /** Valores de b + c onde há fronteira, isto é, retas de `a` constante. */
  readonly diagonais: readonly number[];
  readonly classificar: (ponto: { a: number; b: number; c: number }) => Codigo;
}

/**
 * Regenera as zonas a partir da tabela. Devolve, por código, a lista de anéis
 * — mais de um quando a zona é publicada em regiões disjuntas.
 */
export function gerarZonas<Codigo extends string>(particao: Particao<Codigo>): Map<Codigo, XY[][]> {
  const { cortesB, cortesC, diagonais, classificar } = particao;
  const pecas = new Map<Codigo, XY[][]>();
  for (let i = 0; i < cortesB.length - 1; i++) {
    for (let j = 0; j < cortesC.length - 1; j++) {
      const celula: XY[] = [
        [cortesB[i], cortesC[j]],
        [cortesB[i + 1], cortesC[j]],
        [cortesB[i + 1], cortesC[j + 1]],
        [cortesB[i], cortesC[j + 1]],
      ];
      let subpecas = [cortar(celula, (p) => 100 - p[0] - p[1])];
      // Cada diagonal parte as peças que ela atravessa, nos dois lados.
      for (const d of diagonais) {
        const novas: XY[][] = [];
        for (const sub of subpecas) {
          if (sub.length < 3) continue;
          for (const lado of [(p: XY) => d - p[0] - p[1], (p: XY) => p[0] + p[1] - d]) {
            const peca = cortar(sub, lado);
            if (peca.length >= 3 && Math.abs(areaAssinada(peca)) > 1e-9) novas.push(peca);
          }
        }
        subpecas = novas;
      }
      for (const peca of subpecas) {
        if (peca.length < 3 || Math.abs(areaAssinada(peca)) < 1e-9) continue;
        const cx = peca.reduce((s, p) => s + p[0], 0) / peca.length;
        const cy = peca.reduce((s, p) => s + p[1], 0) / peca.length;
        const codigo = classificar({ a: 100 - cx - cy, b: cx, c: cy });
        pecas.set(codigo, [...(pecas.get(codigo) ?? []), peca]);
      }
    }
  }
  const zonas = new Map<Codigo, XY[][]>();
  for (const [codigo, lista] of pecas) zonas.set(codigo, colar(lista).map(semColineares));
  return zonas;
}

/** Área total das zonas geradas. O simplex em (b, c) tem área 5000. */
export function areaTotal(zonas: Map<string, XY[][]>): number {
  let total = 0;
  for (const aneis of zonas.values()) for (const anel of aneis) total += Math.abs(areaAssinada(anel));
  return total;
}
