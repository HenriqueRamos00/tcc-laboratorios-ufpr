using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendAuth;

public sealed class BackendAuthPasswordRecoveryAdapter(
    HttpClient httpClient, BackendAuthOptions options) : IClientPasswordRecovery
{
    public async Task RequestAsync(string email, CancellationToken cancellationToken = default)
    {
        try
        {
            using HttpRequestMessage request = CreateRequest(
                "api/auth/recoveries", new { email = email.Trim().ToLowerInvariant() });
            using HttpResponseMessage response = await httpClient.SendAsync(request, cancellationToken);
            if (response.StatusCode == HttpStatusCode.Accepted) return;
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
            using HttpRequestMessage request = CreateRequest(
                "api/auth/password-resets", new { token, password });
            using HttpResponseMessage response = await httpClient.SendAsync(request, cancellationToken);
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

    private HttpRequestMessage CreateRequest(string path, object body)
    {
        HttpRequestMessage request = new(HttpMethod.Post, path)
        {
            Content = JsonContent.Create(body)
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", options.ServiceToken);
        return request;
    }
}
