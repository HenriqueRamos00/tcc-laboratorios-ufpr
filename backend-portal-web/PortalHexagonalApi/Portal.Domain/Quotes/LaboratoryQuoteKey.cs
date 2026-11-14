using System.Text.RegularExpressions;

namespace Portal.Domain.Quotes;

/// <summary>
/// A chave que liga um orcamento comercial as linhas do laboratorio. E o valor que o
/// AutoLAB guarda em <c>OrcamentoCRM</c>, na forma nua <c>2024_37569</c>, e a unica coisa
/// que pode atravessar uma porta de laboratorio em nome de um orcamento.
///
/// A regra mora aqui, e nao em cada chamador, porque tres lugares precisam dela (lista e
/// detalhe de orcamentos, e relatorios por orcamento) e porque ela e regra de negocio, nao
/// detalhe de fonte: o codigo da cotacao no Salesforce vem como <c>EAQ_2024_37569_V_1</c>
/// e o laboratorio registra so o ano e o numero. Sao aceitas exatamente tres formas,
/// <c>EAQ_2024_37569_V_1</c>, <c>EAQ_2024_37569</c> e <c>2024_37569</c>, aparadas nas
/// pontas. Qualquer outra coisa nao produz chave, e o orcamento fica sem etapa de
/// laboratorio e sem relatorios em vez de casar com a linha errada.
///
/// O que esta regra nao faz e de proposito: nao busca por prefixo (o sufixo livre de
/// <c>2026_49001_UHAB Agua Boa</c> distingue uma usina da outra) e nao usa o codigo da
/// oportunidade como alternativa, porque ele nao e o codigo que o laboratorio registra.
/// </summary>
public sealed partial record LaboratoryQuoteKey
{
    private LaboratoryQuoteKey(string value)
    {
        Value = value;
    }

    /// <summary>O valor como o AutoLAB o guarda em <c>OrcamentoCRM</c>.</summary>
    public string Value { get; }

    /// <summary>
    /// A chave de laboratorio de um codigo de cotacao, ou nula quando o codigo esta ausente
    /// ou fora das tres formas aceitas.
    /// </summary>
    public static LaboratoryQuoteKey? FromQuotationCode(string? quotationCode)
    {
        if (string.IsNullOrWhiteSpace(quotationCode)) return null;

        Match match = QuotationCodeFormat().Match(quotationCode.Trim());

        return match.Success ? new LaboratoryQuoteKey(match.Groups["code"].Value) : null;
    }

    /// <summary>
    /// Compara com o <c>OrcamentoCRM</c> devolvido pela fonte. Coluna <c>char</c> chega com
    /// espaco a direita, e por isso o valor e aparado; a comparacao e pelo valor inteiro.
    /// </summary>
    public bool Matches(string? laboratoryQuoteCode)
    {
        return laboratoryQuoteCode is not null
            && string.Equals(laboratoryQuoteCode.Trim(), Value, StringComparison.OrdinalIgnoreCase);
    }

    public override string ToString() => Value;

    [GeneratedRegex(@"\A(?:EAQ_)?(?<code>[0-9]{4}_[0-9]+)(?:_V_[0-9]+)?\z", RegexOptions.CultureInvariant)]
    private static partial Regex QuotationCodeFormat();
}
