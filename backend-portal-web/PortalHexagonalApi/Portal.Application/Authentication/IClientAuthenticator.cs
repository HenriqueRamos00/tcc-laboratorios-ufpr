namespace Portal.Application.Authentication;

public interface IClientAuthenticator
{
    Task<ClientAuthenticationResult?> AuthenticateAsync(
        string email, string password, CancellationToken cancellationToken = default);
}
