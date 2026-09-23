import {
  aplicaSeDuval4,
  classificarDuval4,
  duval4,
  gasAusenteDoDuval4,
  ZONAS_DUVAL_4,
  zonasDuval4Com,
  type CodigoDeDuval4,
} from './duval-4';
import type { PontoTernario } from './health';
import { areaTotal, gerarZonas, normalizar, type XY } from './particao-ternaria.spec-util';

// Oráculo dos polígonos do triângulo 4: regenera ZONAS_DUVAL_4 a partir da
// tabela de desigualdades e falha se alguém editar um vértice na mão.
//
// Plano (b, c) = (%CH₄, %C₂H₆), com a = %H₂ = 100 − b − c. As fronteiras em
// %H₂ (9 e 15) são diagonais b + c = 91 e b + c = 85.

const PARTICAO = {
  cortesB: [0, 2, 15, 36, 100],
  cortesC: [0, 1, 24, 30, 46, 100],
  diagonais: [85, 91],
  classificar: classificarDuval4,
};

const comoXY = (vertices: readonly PontoTernario[]): XY[] => vertices.map((v) => [v.b, v.c]);

/** Agrupa por código: uma zona pode ser publicada em mais de um anel. */
function aneisPorCodigo(zonas: readonly { codigo: string; vertices: readonly PontoTernario[] }[]) {
  const porCodigo = new Map<string, string[]>();
  for (const zona of zonas) {
    const lista = porCodigo.get(zona.codigo) ?? [];
    porCodigo.set(zona.codigo, [...lista, normalizar(comoXY(zona.vertices))]);
  }
  for (const [codigo, aneis] of porCodigo) porCodigo.set(codigo, [...aneis].sort());
  return porCodigo;
}

describe('Triângulo de Duval 4', () => {
  const geradas = gerarZonas(PARTICAO);

  it('cobre o triângulo inteiro: sem buraco e sem sobreposição', () => {
    // O simplex em (b, c) é o triângulo retângulo de catetos 100: área 5000.
    // Buraco daria menos; sobreposição daria mais.
    expect(areaTotal(geradas)).toBeCloseTo(5000, 6);
  });

  it('desenha exatamente as zonas que a tabela normativa define', () => {
    const desenhadas = aneisPorCodigo(ZONAS_DUVAL_4);
    expect([...desenhadas.keys()].sort()).toEqual([...geradas.keys()].sort());
    for (const [codigo, aneis] of geradas) {
      const esperados = [...aneis.map(normalizar)].sort();
      expect(desenhadas.get(codigo))
        .withContext(`vértices de ${codigo} divergem da tabela`)
        .toEqual(esperados);
    }
  });

  it('classifica todo ponto do triângulo', () => {
    // Passo fracionário para não cair em cima de fronteira.
    let amostras = 0;
    for (let b = 0.37; b < 100; b += 0.61) {
      for (let c = 0.23; b + c < 100; c += 0.53) {
        expect(classificarDuval4({ a: 100 - b - c, b, c })).toBeTruthy();
        amostras++;
      }
    }
    expect(amostras).toBeGreaterThan(10000);
  });

  it('não computa a coleta sem os três gases, e não a confunde com zero', () => {
    expect(duval4({ h2: 2028, ch4: 601, c2h6: null })).toBeNull();
    expect(duval4({ h2: null, ch4: 601, c2h6: 74 })).toBeNull();
    expect(duval4({ h2: 0, ch4: 0, c2h6: 0 })).toBeNull();
    expect(gasAusenteDoDuval4({ h2: 2028, ch4: null, c2h6: 74 })).toBe('CH₄');
    const ponto = duval4({ h2: 2028, ch4: 601, c2h6: 74 });
    expect(ponto).not.toBeNull();
    expect(ponto!.a + ponto!.b + ponto!.c).toBeCloseTo(100, 9);
  });

  it('respeita as desigualdades meia-abertas nas fronteiras da tabela', () => {
    // PD é a faixa entre %CH₄ = 2 e %CH₄ = 15, com %C₂H₆ < 1.
    expect(classificarDuval4({ a: 98, b: 2, c: 0 })).toBe('PD');
    expect(classificarDuval4({ a: 84.9, b: 15, c: 0.1 })).toBe('S');
    // A cúspide de H₂ quase puro (%CH₄ < 2) cai em S, não em PD: a norma cita
    // um código R para ela no texto, mas a tabela de zonas não o carrega.
    expect(classificarDuval4({ a: 99, b: 1, c: 0 })).toBe('S');
    // %H₂ = 9 separa O de S; a regra é `< 9`, não `<= 9`.
    expect(classificarDuval4({ a: 8, b: 62, c: 30 })).toBe('O');
    expect(classificarDuval4({ a: 9, b: 61, c: 30 })).toBe('S');
    // %C₂H₆ = 46 com %H₂ >= 9 é ND; abaixo de %H₂ = 9 a mesma faixa é O.
    expect(classificarDuval4({ a: 10, b: 0, c: 90 })).toBe('ND');
    expect(classificarDuval4({ a: 5, b: 5, c: 90 })).toBe('O');
    // Zona C: decidimos %C₂H₆ < 24 com %CH₄ >= 36 (ver proveniência).
    expect(classificarDuval4({ a: 0, b: 80, c: 20 })).toBe('C');
    expect(classificarDuval4({ a: 40, b: 36, c: 24 })).toBe('S');
    // Entre %C₂H₆ 24 e 30, quem decide C vs S é a reta %H₂ = 15.
    expect(classificarDuval4({ a: 14, b: 58, c: 28 })).toBe('C');
    expect(classificarDuval4({ a: 16, b: 56, c: 28 })).toBe('S');
  });

  it('só se aplica depois de PD, T1 ou T2 no triângulo 1', () => {
    // IEEE Std C57.104-2019, p. 66, notas a) e b) sob a Tabela D.4.
    expect(aplicaSeDuval4('PD')).toBeTrue();
    expect(aplicaSeDuval4('T1')).toBeTrue();
    expect(aplicaSeDuval4('T2')).toBeTrue();
    expect(aplicaSeDuval4('T3')).toBeFalse();
    expect(aplicaSeDuval4('D1')).toBeFalse();
    expect(aplicaSeDuval4('D2')).toBeFalse();
    expect(aplicaSeDuval4('DT')).toBeFalse();
    // Sem veredito do triângulo 1 não há licença: nada de assumir que vale.
    expect(aplicaSeDuval4(null)).toBeFalse();
  });

  it('acende só as zonas em que caiu leitura', () => {
    const codigos: readonly CodigoDeDuval4[] = ['S'];
    const acesas = zonasDuval4Com(codigos)
      .filter((zona) => zona.destacada)
      .map((zona) => zona.codigo);
    expect(acesas).toEqual(['S']);
    expect(zonasDuval4Com([]).every((zona) => !zona.destacada)).toBeTrue();
  });
});
