namespace Portal.Application.Authentication;

public sealed class AuthenticationNotAvailableException : Exception
{
    public AuthenticationNotAvailableException()
        : base("A autenticação ainda não está disponível para esta fonte.")
    {
    }
}
