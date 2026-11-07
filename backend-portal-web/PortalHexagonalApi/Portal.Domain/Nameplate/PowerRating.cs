using Portal.Domain.Nameplate.Exceptions;

namespace Portal.Domain.Nameplate;

/// <summary>
/// A potencia de placa e a unidade em que ela foi escrita, que sao duas colunas da fonte e um
/// fato so: um numero de potencia sem unidade nao diz quanto, e uma unidade sem numero nao
/// diz nada.
///
/// O tipo existe para que "nunca publicar unidade sem grandeza" seja fato de construcao, e
/// nao disciplina de quem monta a tela. Medido: 100 linhas tem <c>Potencia</c> sem
/// <c>UnidadePotencia</c>, e 7 linhas tem <c>UnidadePotencia</c> sem <c>Potencia</c>. O
/// primeiro caso vira uma potencia sem unidade; o segundo nao vira nada, porque nao ha
/// grandeza a que a unidade pertenca.
///
/// <see cref="Unit"/> sai como a fonte escreve, sem normalizar. As unidades medidas sao
/// <c>kVA</c> com 12257 linhas, <c>MVA</c> com 2167, <c>MVAr</c> com 289, <c>A</c> com 72,
/// <c>KVAr</c> com 56, e ainda <c>kW</c>, <c>kV</c>, <c>VA</c> e <c>kcal</c>. <c>kV</c> e
/// unidade de tensao numa coluna de potencia, e continua saindo <c>kV</c>: corrigir a unidade
/// seria o portal decidir o que o laboratorio quis dizer.
/// </summary>
public sealed record PowerRating
{
    public PowerRating(string value, string? unit)
    {
        if (string.IsNullOrWhiteSpace(value)) throw new PowerUnitWithoutValueException();

        Value = value.Trim();
        Unit = string.IsNullOrWhiteSpace(unit) ? null : unit.Trim();
    }

    /// <summary>
    /// A potencia como a fonte escreveu. Texto, nunca decimal: das 14892 linhas preenchidas,
    /// 11815 sao inteiro, 2925 tem virgula decimal, 128 tem ponto decimal, e as outras nao
    /// sao numero nenhum, entre elas <c>2000/2500</c>, <c>20000/25000</c>, <c>3/3/2,5</c>,
    /// <c>5/6, 25/7</c> e <c>300 KVA</c>, que traz a unidade dentro do valor.
    /// </summary>
    public string Value { get; }

    public string? Unit { get; }
}
