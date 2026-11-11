namespace Portal.Api.LabResults;

/// <summary>
/// Configuracao da fatia de ensaios. <see cref="Source"/> e <see cref="Scope"/> nao tem
/// valor padrao de proposito: um padrao silencioso faria o ambiente errado ler a fonte errada
/// ou mostrar registros que nao deveria.
/// </summary>
public sealed class LabResultsOptions
{
    public const string SectionName = "LabResults";

    public const string NotConnectedSource = "not-connected";

    public const string SqlServerSource = "sql-server";

    public const string AllScope = "all";
    public const string CompanyScope = "company";

    public string? Source { get; init; }

    public string? Scope { get; init; }

    /// <summary>
    /// Empresa usada no escopo <see cref="CompanyScope"/> quando a requisicao nao traz a
    /// claim de inquilino. Existe apenas em ambiente de desenvolvimento, e a ausencia dele faz
    /// o endpoint responder 401 em vez de servir o laudo de um cliente para qualquer um.
    /// </summary>
    public string? DevelopmentTenant { get; init; }
}
