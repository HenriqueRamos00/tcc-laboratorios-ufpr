namespace Portal.Application.Authentication;

public sealed class LoginService(IClientAuthenticator authenticator, IPortalSessionIssuer? issuer = null) : ILoginUseCase
{
    public async Task<ClientSession?> LoginAsync(
        string email, string password, CancellationToken cancellationToken)
    {
        ClientAuthenticationResult? authenticated = await authenticator.AuthenticateAsync(email, password, cancellationToken);
        if (authenticated is null) return null;
        if (authenticated.ExistingSession is not null) return authenticated.ExistingSession;
        return (issuer ?? throw new InvalidOperationException("Emissor de sessão do portal não configurado."))
            .Issue(authenticated.User);
    }
}
