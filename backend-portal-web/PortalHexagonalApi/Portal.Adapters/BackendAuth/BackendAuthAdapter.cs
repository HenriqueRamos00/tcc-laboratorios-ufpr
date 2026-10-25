using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.RegularExpressions;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendAuth;

public sealed class BackendAuthAdapter(HttpClient httpClient, BackendAuthOptions options) : IClientAuthenticator
{
    public async Task<ClientAuthenticationResult?> AuthenticateAsync(
        string email, string password, CancellationToken cancellationToken = default)
    {
        try
        {
            using HttpRequestMessage request = new(HttpMethod.Post, "api/auth/login")
            {
                Content = JsonContent.Create(new { email = email.Trim().ToLowerInvariant(), password })
            };
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", options.ServiceToken);
            using HttpResponseMessage response = await httpClient.SendAsync(request, cancellationToken);

            if (response.StatusCode == HttpStatusCode.Unauthorized && !response.Headers.WwwAuthenticate.Any()) return null;
            if (response.StatusCode == HttpStatusCode.BadRequest) throw new LoginRequestRejectedException();
            if (response.StatusCode == HttpStatusCode.TooManyRequests)
            {
                double? seconds = response.Headers.RetryAfter?.Delta?.TotalSeconds;
                if (seconds is > 0 and <= 86400)
                    throw new LoginCooldownException((long)Math.Ceiling(seconds.Value));
                throw new AuthenticationSourceUnavailableException("A fonte retornou bloqueio sem período de espera válido.");
            }
            if (response.StatusCode != HttpStatusCode.OK)
                throw new AuthenticationSourceUnavailableException($"A fonte de autenticação respondeu HTTP {(int)response.StatusCode}.");

            BackendAuthUser? user = await response.Content.ReadFromJsonAsync<BackendAuthUser>(cancellationToken);
            if (!IsValid(user))
                throw new AuthenticationSourceUnavailableException("A fonte retornou uma identidade incompleta ou de outro serviço.");

            return new ClientAuthenticationResult(new ClientUser(user!.Id!, user.Name!, user.Email!, user.Role!, null,
                user.ServiceCode, new ClientAuthorizations(user.Authorizations!.CompanyCnpjs!.Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray())));
        }
        catch (HttpRequestException)
        {
            throw new AuthenticationSourceUnavailableException("Não foi possível consultar a fonte de autenticação.");
        }
        catch (JsonException)
        {
            throw new AuthenticationSourceUnavailableException("A resposta da fonte de autenticação é inválida.");
        }
        catch (NotSupportedException)
        {
            throw new AuthenticationSourceUnavailableException("O formato da resposta da fonte de autenticação é inválido.");
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            throw new AuthenticationSourceUnavailableException("A fonte de autenticação excedeu o tempo de resposta.");
        }
    }

    private bool IsValid(BackendAuthUser? user) => user is { Role: "CLIENTE", Authorizations.CompanyCnpjs: not null }
        && !string.IsNullOrWhiteSpace(user.Id) && !string.IsNullOrWhiteSpace(user.Name)
        && !string.IsNullOrWhiteSpace(user.Email)
        && string.Equals(user.ServiceCode, options.ServiceCode, StringComparison.Ordinal)
        && user.Authorizations.CompanyCnpjs.All(cnpj => cnpj is not null && Regex.IsMatch(cnpj, "^[A-Z0-9]{12}[0-9]{2}$"));

    private sealed record BackendAuthUser(string? Id, string? Name, string? Email, string? Role,
        string? ServiceCode, BackendAuthAuthorizations? Authorizations);
    private sealed record BackendAuthAuthorizations(IReadOnlyList<string>? CompanyCnpjs);
}
