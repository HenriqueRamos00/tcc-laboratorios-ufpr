namespace Portal.Application.Authentication;

public interface IClientAuthenticator
{
    Task<ClientSession?> AuthenticateAsync(
        string email, string password, CancellationToken cancellationToken = default);
}
