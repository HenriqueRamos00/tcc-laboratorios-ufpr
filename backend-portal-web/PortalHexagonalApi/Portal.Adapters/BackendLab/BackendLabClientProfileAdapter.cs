using System.Net;
using System.Net.Http.Json;
using System.Text.Json;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendLab;

public sealed class BackendLabClientProfileAdapter(HttpClient httpClient) : IClientProfileOperations
{
    public Task<ClientProfile> GetAsync(string accountId, CancellationToken cancellationToken = default) =>
        SendJsonAsync<ClientProfile>("api/client/profile", HttpMethod.Get, null,
            HttpStatusCode.OK, cancellationToken);

    public Task<ClientProfile> UpdateAsync(string accountId, ClientProfileUpdate update,
        CancellationToken cancellationToken = default)
    {
        Dictionary<string, object?> payload = new() { ["name"] = update.Name };
        if (update.Phone is not null) payload["phone"] = update.Phone;
        if (update.Position is not null) payload["position"] = update.Position;
        return SendJsonAsync<ClientProfile>("api/client/profile", HttpMethod.Patch, payload,
            HttpStatusCode.OK, cancellationToken);
    }

    public Task ChangePasswordAsync(string accountId, string currentPassword, string newPassword,
        CancellationToken cancellationToken = default) =>
        SendNoContentAsync("api/client/profile/password", HttpMethod.Post,
            new { currentPassword, newPassword }, cancellationToken);

    public Task<ClientPendingEmailChange> RequestEmailChangeAsync(string accountId, string newEmail,
        string currentPassword, CancellationToken cancellationToken = default) =>
        SendJsonAsync<ClientPendingEmailChange>("api/client/profile/email-change", HttpMethod.Post,
            new { newEmail, currentPassword }, HttpStatusCode.Accepted, cancellationToken);

    public Task CancelEmailChangeAsync(string accountId, CancellationToken cancellationToken = default) =>
        SendNoContentAsync("api/client/profile/email-change", HttpMethod.Delete, new { }, cancellationToken);

    public Task<EmailChangeConfirmation> ConfirmEmailChangeAsync(string token,
        CancellationToken cancellationToken = default) =>
        SendJsonAsync<EmailChangeConfirmation>("api/client/profile/email-change/confirm", HttpMethod.Post,
            new { token }, HttpStatusCode.OK, cancellationToken);

    private async Task<T> SendJsonAsync<T>(string path, HttpMethod method, object? payload,
        HttpStatusCode expected, CancellationToken cancellationToken)
    {
        using HttpRequestMessage request = new(method, path);
        if (payload is not null) request.Content = JsonContent.Create(payload);
        using HttpResponseMessage response = await SendAsync(request, cancellationToken);
        if (response.StatusCode != expected)
            throw await FailureAsync(response, cancellationToken);
        try
        {
            return await response.Content.ReadFromJsonAsync<T>(cancellationToken)
                ?? throw new AuthenticationSourceUnavailableException("A fonte retornou uma resposta vazia.");
        }
        catch (JsonException failure)
        {
            throw new AuthenticationSourceUnavailableException("A resposta da fonte do perfil é inválida.", failure);
        }
    }

    private async Task SendNoContentAsync(string path, HttpMethod method, object payload,
        CancellationToken cancellationToken)
    {
        using HttpRequestMessage request = new(method, path) { Content = JsonContent.Create(payload) };
        using HttpResponseMessage response = await SendAsync(request, cancellationToken);
        if (response.StatusCode != HttpStatusCode.NoContent)
            throw await FailureAsync(response, cancellationToken);
    }

    private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        try
        {
            return await httpClient.SendAsync(request, cancellationToken);
        }
        catch (HttpRequestException failure)
        {
            throw new AuthenticationSourceUnavailableException(
                "Não foi possível consultar a fonte do perfil.", failure);
        }
        catch (OperationCanceledException failure) when (!cancellationToken.IsCancellationRequested)
        {
            throw new AuthenticationSourceUnavailableException(
                "A fonte do perfil excedeu o tempo de resposta.", failure);
        }
    }

    private static async Task<ProfileOperationException> FailureAsync(
        HttpResponseMessage response, CancellationToken cancellationToken)
    {
        if (response.StatusCode == HttpStatusCode.Unauthorized)
            return new ProfileOperationException("session-expired", (int)response.StatusCode);
        if (response.StatusCode == HttpStatusCode.Forbidden)
            return new ProfileOperationException("forbidden", (int)response.StatusCode);
        if (response.StatusCode == HttpStatusCode.NotFound)
            return new ProfileOperationException("session-expired", (int)HttpStatusCode.Unauthorized);
        if ((int)response.StatusCode >= 500)
            throw new AuthenticationSourceUnavailableException(
                $"A fonte do perfil respondeu HTTP {(int)response.StatusCode}.");

        BackendProblem? problem;
        try
        {
            problem = await response.Content.ReadFromJsonAsync<BackendProblem>(cancellationToken);
        }
        catch (JsonException failure)
        {
            throw new AuthenticationSourceUnavailableException("A resposta da fonte do perfil é inválida.", failure);
        }
        string code = problem?.Code ?? (response.StatusCode == HttpStatusCode.BadRequest
            ? "invalid-profile" : "profile-source-unavailable");
        long? retryAfter = response.Headers.RetryAfter?.Delta is TimeSpan wait
            ? Math.Max(1, (long)Math.Ceiling(wait.TotalSeconds)) : null;
        return new ProfileOperationException(code, (int)response.StatusCode, retryAfter);
    }

    private sealed record BackendProblem(string? Code);
}
