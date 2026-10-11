namespace Portal.Domain.LabResults.Exceptions;

/// <summary>
/// Analise sem protocolo Lactec. O protocolo e a identidade: foi a unica chave presente,
/// nao nula e unica em todo o dado observado, e sem ele a analise nao tem como ser
/// endereçada de volta.
/// </summary>
public sealed class AnalysisWithoutProtocolException : Exception
{
    public AnalysisWithoutProtocolException()
        : base("Analise sem protocolo Lactec nao tem identidade.")
    {
    }
}
