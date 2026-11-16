namespace Portal.Application.LabResults.Exceptions;

public sealed class ClientLabAuthenticationRequiredException : Exception
{
    public ClientLabAuthenticationRequiredException() : base("A fonte de dados exige autenticação.") { }
}
