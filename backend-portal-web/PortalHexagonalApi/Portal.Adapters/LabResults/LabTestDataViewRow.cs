using System.Text.Json.Serialization;

namespace Portal.Adapters.LabResults;

/// <summary>
/// Uma linha de <c>vwDadosEnsaios</c>, com os nomes das colunas da view. Mora no adaptador
/// porque e vocabulario da fonte, nao do dominio.
///
/// O formato embutido e JSON Lines, e nao TSV, por uma razao estrutural: o dado real
/// distingue <c>NULL</c> de texto vazio, e campo vazio de TSV nao distingue os dois sem
/// inventar sentinela. Aqui <c>null</c> e ausencia e <c>""</c> e texto vazio, e as quatro
/// formas de resultado sobrevivem a ida e volta pelo arquivo.
/// </summary>
public sealed record LabTestDataViewRow
{
    [JsonPropertyName("identificacao")]
    public string Identificacao { get; init; } = string.Empty;

    [JsonPropertyName("protocoloLactec")]
    public string ProtocoloLactec { get; init; } = string.Empty;

    [JsonPropertyName("numSerie")]
    public string? NumSerie { get; init; }

    [JsonPropertyName("name")]
    public string Name { get; init; } = string.Empty;

    [JsonPropertyName("resultado")]
    public string? Resultado { get; init; }

    /// <summary>
    /// A coluna da view se chama <c>EmpresaDoTransformador</c>. Aqui ela vem escrita por extenso porque
    /// "trafo" e abreviacao de oficina, nao nome de campo, e quem le a amostra meses depois
    /// nao tem como adivinhar o que ela quer dizer.
    /// </summary>
    [JsonPropertyName("empresaDoTransformador")]
    public string EmpresaDoTransformador { get; init; } = string.Empty;

    [JsonPropertyName("dataEmissao")]
    public DateTime DataEmissao { get; init; }

    [JsonPropertyName("dataFinalAnalise")]
    public DateTime DataFinalAnalise { get; init; }
}
