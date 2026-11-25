using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendAuth;

public sealed class BackendAuthEmailConfirmationAdapter(
    HttpClient httpClient, BackendAuthOptions options) : IClientEmailConfirmation
{
    public async Task<bool> ConfirmAsync(string token, CancellationToken cancellationToken = default)
    {
        try
        {
            using HttpRequestMessage request = new(HttpMethod.Post, "api/auth/confirmations")
            {
                Content = JsonContent.Create(new { token })
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", options.ServiceToken);
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
}
