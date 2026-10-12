namespace Portal.Application.Authentication;

public sealed class LoginRequestRejectedException : Exception
{
    public LoginRequestRejectedException() : base("Os dados de login foram recusados pela fonte de autenticação.")
    {
    }
}
