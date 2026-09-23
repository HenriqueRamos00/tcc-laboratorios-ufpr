import {
  aplicaSeDuval5,
  classificarDuval5,
  duval5,
  gasAusenteDoDuval5,
  ZONAS_DUVAL_5,
  zonasDuval5Com,
  type CodigoDeDuval5,
} from './duval-5';
import type { PontoTernario } from './health';
import { areaTotal, gerarZonas, normalizar, type XY } from './particao-ternaria.spec-util';

// Oráculo dos polígonos do triângulo 5: regenera ZONAS_DUVAL_5 a partir da
// tabela de desigualdades e falha se alguém editar um vértice na mão.
//
// Plano (b, c) = (%C₂H₄, %C₂H₆), com a = %CH₄ = 100 − b − c. A Tabela D.4 não
// tem fronteira em %CH₄ — ele é o vértice dependente — então não há diagonal.

const PARTICAO = {
  cortesB: [0, 1, 10, 35, 50, 70, 100],
  cortesC: [0, 2, 12, 14, 30, 54, 100],
  diagonais: [] as readonly number[],
  classificar: classificarDuval5,
};

const comoXY = (vertices: readonly PontoTernario[]): XY[] => vertices.map((v) => [v.b, v.c]);

/** Agrupa por código: a zona O é publicada em dois anéis disjuntos. */
function aneisPorCodigo(zonas: readonly { codigo: string; vertices: readonly PontoTernario[] }[]) {
  const porCodigo = new Map<string, string[]>();
  for (const zona of zonas) {
    const lista = porCodigo.get(zona.codigo) ?? [];
    porCodigo.set(zona.codigo, [...lista, normalizar(comoXY(zona.vertices))]);
  }
  for (const [codigo, aneis] of porCodigo) porCodigo.set(codigo, [...aneis].sort());
  return porCodigo;
}

describe('Triângulo de Duval 5', () => {
  const geradas = gerarZonas(PARTICAO);

  it('cobre o triângulo inteiro: sem buraco e sem sobreposição', () => {
    expect(areaTotal(geradas)).toBeCloseTo(5000, 6);
  });

  it('desenha exatamente as zonas que a tabela normativa define', () => {
    const desenhadas = aneisPorCodigo(ZONAS_DUVAL_5);
    expect([...desenhadas.keys()].sort()).toEqual([...geradas.keys()].sort());
    for (const [codigo, aneis] of geradas) {
      const esperados = [...aneis.map(normalizar)].sort();
      expect(desenhadas.get(codigo))
        .withContext(`vértices de ${codigo} divergem da tabela`)
        .toEqual(esperados);
    }
  });

  it('desenha a zona O em dois anéis, como a figura publicada', () => {
    // Não é duplicata: a figura da norma rotula "O" duas vezes, em regiões
    // disjuntas. Se alguém "consertar" isso fundindo as duas, o teste cai.
    expect(ZONAS_DUVAL_5.filter((zona) => zona.codigo === 'O').length).toBe(2);
    expect(geradas.get('O')?.length).toBe(2);
  });

  it('não usa a nomenclatura T2-H / T3-H', () => {
    // "T3-H" é zona do Pentágono 2, não do triângulo 5, e "T2-H" não existe
    // em fonte publicada nenhuma. A versão anterior desta tela usava as duas.
    const codigos = ZONAS_DUVAL_5.map((zona) => zona.codigo);
    expect(codigos).toContain('T2');
    expect(codigos).toContain('T3');
    expect(codigos.some((codigo) => codigo.endsWith('-H'))).toBeFalse();
  });

  it('classifica todo ponto do triângulo', () => {
    let amostras = 0;
    for (let b = 0.37; b < 100; b += 0.61) {
      for (let c = 0.23; b + c < 100; c += 0.53) {
        expect(classificarDuval5({ a: 100 - b - c, b, c })).toBeTruthy();
        amostras++;
      }
    }
    expect(amostras).toBeGreaterThan(10000);
  });

  it('não computa a coleta sem os três gases, e não a confunde com zero', () => {
    expect(duval5({ ch4: 3600, c2h4: 3800, c2h6: null })).toBeNull();
    expect(duval5({ ch4: null, c2h4: 3800, c2h6: 2400 })).toBeNull();
    expect(duval5({ ch4: 0, c2h4: 0, c2h6: 0 })).toBeNull();
    expect(gasAusenteDoDuval5({ ch4: 3600, c2h4: null, c2h6: 2400 })).toBe('C₂H₄');
    const ponto = duval5({ ch4: 3600, c2h4: 3800, c2h6: 2400 });
    expect(ponto).not.toBeNull();
    expect(ponto!.a + ponto!.b + ponto!.c).toBeCloseTo(100, 9);
  });

  it('respeita as desigualdades meia-abertas da Tabela D.4', () => {
    expect(classificarDuval5({ a: 98, b: 0, c: 2 })).toBe('PD');
    // %C₂H₆ = 14 (e não 15) separa PD/O de S — decisão documentada no módulo.
    expect(classificarDuval5({ a: 86, b: 0, c: 14 })).toBe('S');
    expect(classificarDuval5({ a: 85.6, b: 0, c: 14.4 })).toBe('S');
    expect(classificarDuval5({ a: 86.1, b: 0, c: 13.9 })).toBe('PD');
    // %C₂H₄ = 1 separa PD de O na mesma faixa de C₂H₆.
    expect(classificarDuval5({ a: 85, b: 1, c: 14 })).toBe('S');
    expect(classificarDuval5({ a: 87, b: 1, c: 12 })).toBe('O');
    // %C₂H₆ = 54 devolve à zona O, no canto do etano.
    expect(classificarDuval5({ a: 46, b: 0, c: 54 })).toBe('O');
    expect(classificarDuval5({ a: 46.1, b: 0, c: 53.9 })).toBe('S');
    expect(classificarDuval5({ a: 88, b: 10, c: 2 })).toBe('T2');
    expect(classificarDuval5({ a: 60, b: 10, c: 30 })).toBe('ND');
    // Entre %C₂H₆ 12 e 14 quem decide C vs T3 é a reta %C₂H₄ = 50.
    expect(classificarDuval5({ a: 53, b: 35, c: 12 })).toBe('C');
    expect(classificarDuval5({ a: 38, b: 50, c: 12 })).toBe('T3');
    // Acima de %C₂H₆ = 14 a fronteira de C anda para %C₂H₄ = 70.
    expect(classificarDuval5({ a: 36, b: 50, c: 14 })).toBe('C');
    expect(classificarDuval5({ a: 16, b: 70, c: 14 })).toBe('T3');
  });

  it('fecha em O a fresta que a Tabela D.4 deixou sem zona', () => {
    // %C₂H₄ >= 1 e < 10 com %C₂H₆ < 2 não casa com nenhuma linha publicada:
    // 0,36% da área. Atribuímos a O, como todas as implementações. Se um dia
    // alguém achar a linha que falta na norma, este teste é o lugar de mudar.
    expect(classificarDuval5({ a: 94, b: 5, c: 1 })).toBe('O');
    expect(classificarDuval5({ a: 98.5, b: 1, c: 0.5 })).toBe('O');
  });

  it('só se aplica depois de T2 ou T3 no triângulo 1', () => {
    // IEEE Std C57.104-2019, p. 66, notas a) e c) sob a Tabela D.4.
    expect(aplicaSeDuval5('T2')).toBeTrue();
    expect(aplicaSeDuval5('T3')).toBeTrue();
    expect(aplicaSeDuval5('PD')).toBeFalse();
    expect(aplicaSeDuval5('T1')).toBeFalse();
    expect(aplicaSeDuval5('D1')).toBeFalse();
    expect(aplicaSeDuval5('D2')).toBeFalse();
    expect(aplicaSeDuval5('DT')).toBeFalse();
    expect(aplicaSeDuval5(null)).toBeFalse();
  });

  it('acende só as zonas em que caiu leitura, nos dois anéis de O', () => {
    const codigos: readonly CodigoDeDuval5[] = ['O'];
    expect(zonasDuval5Com(codigos).filter((zona) => zona.destacada).length).toBe(2);
    expect(zonasDuval5Com([]).every((zona) => !zona.destacada)).toBeTrue();
  });
});
