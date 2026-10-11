namespace Portal.Api.LabResults;

/// <summary>
/// Configuracao da fatia de ensaios. <see cref="Source"/> nao tem valor padrao de
/// proposito: um padrao silencioso faria o ambiente errado servir a amostra embutida como
/// se fosse o AutoLAB.
/// </summary>
public sealed class LabResultsOptions
{
    public const string SectionName = "LabResults";

    public const string EmbeddedSampleSource = "embedded-sample";
    public const string SqlServerSource = "sql-server";

    public string? Source { get; init; }

    /// <summary>
    /// Inquilino usado quando a requisicao nao traz um principal autenticado. Existe apenas
    /// em ambiente de desenvolvimento, e a ausencia dele faz o endpoint responder 401 em
    /// vez de servir o laudo de um cliente para qualquer um.
    /// </summary>
    public string? DevelopmentTenant { get; init; }
}
