namespace Portal.Application.LabResults.Exceptions;

public sealed class ClientLabAccessForbiddenException : Exception
{
    public ClientLabAccessForbiddenException() : base("A fonte negou o acesso ao recurso solicitado.") { }
}
