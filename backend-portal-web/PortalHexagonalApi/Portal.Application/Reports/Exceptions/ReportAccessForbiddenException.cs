namespace Portal.Application.Reports.Exceptions;

public sealed class ReportAccessForbiddenException : Exception
{
    public ReportAccessForbiddenException()
        : base("O usuário autenticado não tem acesso ao relatório.")
    {
    }
}
