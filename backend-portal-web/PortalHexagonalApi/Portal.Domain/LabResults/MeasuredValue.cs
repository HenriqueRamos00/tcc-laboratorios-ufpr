using System.Globalization;

namespace Portal.Domain.LabResults;

/// <summary>
/// O resultado de um item medido tem exatamente quatro formas, e as quatro significam coisas
/// diferentes. Colapsar qualquer uma nas outras faz o laudo afirmar o que ninguem mediu:
/// tratar ausencia como zero inventa um ensaio, e tratar nao detectado como ausencia
/// esconde que o laboratorio esteve la. A hierarquia e fechada por construtor privado,
/// para que um caso novo precise ser declarado aqui e nao surja por heranca em outro
/// projeto.
/// </summary>
public abstract record MeasuredValue
{
    /// <summary>
    /// O laboratorio escreve o decimal com virgula. Pedir "a cultura do Brasil" traria junto
    /// moeda, calendario, nome de mes e separador de milhar, nada disso usado aqui; este
    /// formato declara a unica regra que importa para ler o resultado.
    /// </summary>
    private static readonly NumberFormatInfo DecimalWrittenWithComma = new()
    {
        NumberDecimalSeparator = ","
    };

    /// <summary>
    /// Sem <c>AllowThousands</c> de proposito: no texto do laboratorio o ponto nao separa
    /// milhar, e aceita-lo faria "1.934" virar mil novecentos e trinta e quatro quando o que
    /// foi escrito e um virgula novecentos e trinta e quatro.
    /// </summary>
    private const NumberStyles StyleWithoutThousandsSeparator = NumberStyles.AllowLeadingWhite
        | NumberStyles.AllowTrailingWhite
        | NumberStyles.AllowLeadingSign
        | NumberStyles.AllowDecimalPoint;

    private const string NotDetectedText = "ND";

    private MeasuredValue()
    {
    }

    public static MeasuredValue InterpretResult(string? resultFromSource)
    {
        if (string.IsNullOrWhiteSpace(resultFromSource))
            return TestNotPerformed.Instance;

        string trimmedResult = resultFromSource.Trim();

        if (string.Equals(trimmedResult, NotDetectedText, StringComparison.OrdinalIgnoreCase))
            return NotDetected.Instance;

        return decimal.TryParse(
            trimmedResult,
            StyleWithoutThousandsSeparator,
            DecimalWrittenWithComma,
            out decimal quantity)
            ? new Quantity(quantity)
            : new UninterpretedText(trimmedResult);
    }

    /// <summary>
    /// O ensaio foi feito e deu aquilo.
    /// </summary>
    public sealed record Quantity(decimal NumericValue) : MeasuredValue;

    /// <summary>
    /// O ensaio foi feito e o item medido nao foi detectado. Vale zero em soma de totalizador,
    /// e nao vale zero como medicao publicada.
    /// </summary>
    public sealed record NotDetected : MeasuredValue
    {
        public static readonly NotDetected Instance = new();
    }

    /// <summary>
    /// Aquele ensaio nao foi realizado. Nao ha medicao, e nao ha o que somar.
    /// </summary>
    public sealed record TestNotPerformed : MeasuredValue
    {
        public static readonly TestNotPerformed Instance = new();
    }

    /// <summary>
    /// Veio texto que nenhuma estrategia de parse aceita, como "0,01%". O ensaio foi feito,
    /// entao nao e ausencia; o valor nao e numero, entao nao pode ser publicado como numero.
    /// Uma celula assim degrada a propria linha e nunca o laudo inteiro.
    /// </summary>
    public sealed record UninterpretedText(string OriginalText) : MeasuredValue;
}
