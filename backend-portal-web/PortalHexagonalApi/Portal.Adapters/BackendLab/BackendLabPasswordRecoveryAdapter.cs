using System.Net;
using System.Net.Http.Json;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendLab;

public sealed class BackendLabPasswordRecoveryAdapter(HttpClient httpClient) : IClientPasswordRecovery
{
    public async Task RequestAsync(string email, CancellationToken cancellationToken = default)
    {
        try
        {
            using HttpResponseMessage response = await httpClient.PostAsJsonAsync(
                "api/auth/password/forgot", new { email }, cancellationToken);
            if (response.StatusCode == HttpStatusCode.NoContent) return;
            throw new AuthenticationSourceUnavailableException(
                $"A fonte de autenticação respondeu HTTP {(int)response.StatusCode}.");
        }
        catch (HttpRequestException failure)
        {
            throw new AuthenticationSourceUnavailableException(
                "Não foi possível consultar a fonte de autenticação.", failure);
        }
        catch (OperationCanceledException failure) when (!cancellationToken.IsCancellationRequested)
        {
            throw new AuthenticationSourceUnavailableException(
                "A fonte de autenticação excedeu o tempo de resposta.", failure);
        }
    }

    public async Task<bool> ResetAsync(
        string token, string password, CancellationToken cancellationToken = default)
    {
        try
        {
            using HttpResponseMessage response = await httpClient.PostAsJsonAsync(
                "api/auth/password/reset", new { token, password }, cancellationToken);
            if (response.StatusCode == HttpStatusCode.NoContent) return true;
            if (response.StatusCode == HttpStatusCode.BadRequest) return false;
            throw new AuthenticationSourceUnavailableException(
                $"A fonte de autenticação respondeu HTTP {(int)response.StatusCode}.");
        }
        catch (HttpRequestException failure)
        {
            throw new AuthenticationSourceUnavailableException(
                "Não foi possível consultar a fonte de autenticação.", failure);
        }
        catch (OperationCanceledException failure) when (!cancellationToken.IsCancellationRequested)
        {
            throw new AuthenticationSourceUnavailableException(
                "A fonte de autenticação excedeu o tempo de resposta.", failure);
        }
    }
}
