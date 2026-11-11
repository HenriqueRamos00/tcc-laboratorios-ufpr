namespace Portal.Application.Reports.Exceptions;

public sealed class ReportAuthenticationRequiredException : Exception
{
    public ReportAuthenticationRequiredException()
        : base("Autenticação necessária para consultar relatórios publicados.")
    {
    }

    public ReportAuthenticationRequiredException(Exception innerException)
        : base("Autenticação necessária para consultar relatórios publicados.", innerException)
    {
    }
}
