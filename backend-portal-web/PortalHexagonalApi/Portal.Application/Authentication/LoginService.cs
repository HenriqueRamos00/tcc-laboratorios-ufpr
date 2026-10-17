namespace Portal.Application.Authentication;

public sealed class LoginService : ILoginUseCase
{
    private readonly IClientAuthenticator _authenticator;

    public LoginService(IClientAuthenticator authenticator)
    {
        _authenticator = authenticator;
    }

    public Task<ClientSession?> LoginAsync(
        string email, string password, CancellationToken cancellationToken)
    {
        return _authenticator.AuthenticateAsync(email, password, cancellationToken);
    }
}
