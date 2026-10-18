using Portal.Application.Authentication;

namespace Portal.Adapters.Authentication;

public sealed class UnavailableClientAuthenticator : IClientAuthenticator
{
    // Quando houver o contrato de autenticação Lactec, substituímos essa implementação
    // pelo adapter real, preservando a porta, o caso de uso e o controller.
    public Task<ClientSession?> AuthenticateAsync(
        string email, string password, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        return Task.FromException<ClientSession?>(new AuthenticationNotAvailableException());
    }
}
