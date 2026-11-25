namespace Portal.Application.CompanyRecords.Exceptions;

/// <summary>
/// A fonte dos registros das empresas nao respondeu. Quem pergunta traduz para a falha da
/// propria conversa (ensaios, relatorios ou etapas), e a resposta nunca e "sem escopo".
/// </summary>
public sealed class CompanyRecordsSourceUnavailableException : Exception
{
    public CompanyRecordsSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
