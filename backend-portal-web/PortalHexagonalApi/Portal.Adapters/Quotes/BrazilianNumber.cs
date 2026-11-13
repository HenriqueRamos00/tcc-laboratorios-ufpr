using System.Globalization;

namespace Portal.Adapters.Quotes;

/// <summary>
/// Os numeros como o orcamento os imprime. So o separador entra no formato: pedir "a cultura
/// do Brasil" traria junto moeda, calendario e um separador de milhar que ninguem declarou,
/// e a cultura do processo nao e escolha de quem escreve o documento.
/// </summary>
internal static class BrazilianNumber
{
    private static readonly NumberFormatInfo Separators = new()
    {
        NumberDecimalSeparator = ",",
        NumberGroupSeparator = ".",
        NumberGroupSizes = [3],
    };

    public static string Amount(decimal value) => $"R$ {value.ToString("N2", Separators)}";

    public static string Quantity(decimal value) => value.ToString("N2", Separators);
}
