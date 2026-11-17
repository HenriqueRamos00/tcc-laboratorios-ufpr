namespace Portal.Api.LabResults;

/// <summary>
/// Configuracao da fatia de ensaios. <see cref="Source"/> nao tem valor padrao de proposito:
/// um padrao silencioso faria o ambiente errado ler a fonte errada. A visibilidade nao e
/// configuracao: quem ve o que e decidido pelos CNPJs do token, sempre.
/// </summary>
public sealed class LabResultsOptions
{
    public const string SectionName = "LabResults";

    public const string NotConnectedSource = "not-connected";

    public const string SqlServerSource = "sql-server";
    public const string BackendLabSource = "backend-lab";

    public string? Source { get; init; }
}
