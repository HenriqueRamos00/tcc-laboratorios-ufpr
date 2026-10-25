using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendLab;

public sealed class BackendLabAdapter(HttpClient httpClient) : IClientAuthenticator
{
    public async Task<ClientAuthenticationResult?> AuthenticateAsync(
        string email, string password, CancellationToken cancellationToken = default)
    {
        try
        {
            using HttpResponseMessage response = await httpClient.PostAsJsonAsync(
                "api/auth/login", new { email, password }, cancellationToken);

            if (response.StatusCode == HttpStatusCode.Unauthorized) return null;
            if (response.StatusCode == HttpStatusCode.BadRequest) throw new LoginRequestRejectedException();

            if (response.StatusCode != HttpStatusCode.OK)
                throw new AuthenticationSourceUnavailableException(
                    $"A fonte de autenticação respondeu HTTP {(int)response.StatusCode}.");

            ClientSession? session = await response.Content.ReadFromJsonAsync<ClientSession>(cancellationToken);

            if (!HasValidSession(session))
                throw new AuthenticationSourceUnavailableException("A fonte de autenticação retornou uma sessão incompleta.");

            return new ClientAuthenticationResult(session!.User, session);
        }
        catch (HttpRequestException failure)
        {
            throw new AuthenticationSourceUnavailableException("Não foi possível consultar a fonte de autenticação.", failure);
        }
        catch (JsonException failure)
        {
            throw new AuthenticationSourceUnavailableException("A resposta da fonte de autenticação é inválida.", failure);
        }
        catch (OperationCanceledException failure) when (!cancellationToken.IsCancellationRequested)
        {
            throw new AuthenticationSourceUnavailableException("A fonte de autenticação excedeu o tempo de resposta.", failure);
        }
    }

    private static bool HasValidSession(ClientSession? session) =>
        session is not null
        && !string.IsNullOrWhiteSpace(session.AccessToken)
        && string.Equals(session.TokenType, "Bearer", StringComparison.OrdinalIgnoreCase)
        && session.ExpiresIn > 0
        && session.User is { Role: "Client", Companies: not null } user
        && !string.IsNullOrWhiteSpace(user.Id)
        && !string.IsNullOrWhiteSpace(user.Name)
        && !string.IsNullOrWhiteSpace(user.Email)
        && user.Companies.All(company => company is not null
            && !string.IsNullOrWhiteSpace(company.Id)
            && !string.IsNullOrWhiteSpace(company.Name));
}
