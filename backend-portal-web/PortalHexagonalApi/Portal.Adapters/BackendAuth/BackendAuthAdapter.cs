using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.RegularExpressions;

using Portal.Application.Authentication;

namespace Portal.Adapters.BackendAuth;

public sealed class BackendAuthAdapter(HttpClient httpClient, BackendAuthOptions options) :
    IClientAuthenticator, IClientProfileOperations, IClientSessionStateVerifier
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
            AddServiceToken(request);
            using HttpResponseMessage response = await SendAsync(request, cancellationToken);

            if (response.StatusCode == HttpStatusCode.Unauthorized
                && !response.Headers.WwwAuthenticate.Any()) return null;
            if (response.StatusCode == HttpStatusCode.BadRequest) throw new LoginRequestRejectedException();
            if (response.StatusCode == HttpStatusCode.TooManyRequests)
            {
                double? seconds = response.Headers.RetryAfter?.Delta?.TotalSeconds;
                if (seconds is > 0 and <= 86400)
                    throw new LoginCooldownException((long)Math.Ceiling(seconds.Value));
                throw new AuthenticationSourceUnavailableException("A fonte retornou bloqueio sem período de espera válido.");
            }
            if (response.StatusCode != HttpStatusCode.OK)
                throw SourceFailure(response.StatusCode);

            BackendAuthUser? user = await response.Content.ReadFromJsonAsync<BackendAuthUser>(cancellationToken);
            if (!IsValid(user))
                throw new AuthenticationSourceUnavailableException("A fonte retornou uma identidade incompleta ou de outro serviço.");

            return new ClientAuthenticationResult(new ClientUser(user!.Id!, user.Name!, user.Email!, user.Role!, null,
                user.ServiceCode, new ClientAuthorizations(user.Authorizations!.CompanyCnpjs!.Distinct(StringComparer.Ordinal)
                    .Order(StringComparer.Ordinal).ToArray()), user.CredentialVersion));
        }
        catch (HttpRequestException failure)
        {
            throw SourceFailure(failure);
        }
        catch (JsonException failure)
        {
            throw SourceFailure(failure);
        }
        catch (NotSupportedException failure)
        {
            throw SourceFailure(failure);
        }
        catch (OperationCanceledException failure) when (!cancellationToken.IsCancellationRequested)
        {
            throw SourceFailure(failure);
        }
    }

    public async Task<ClientProfile> GetAsync(string accountId, CancellationToken cancellationToken = default)
    {
        string path = $"api/self-service/accounts/{Uri.EscapeDataString(accountId)}/profile";
        using HttpRequestMessage request = new(HttpMethod.Get, path);
        return await SendJsonAsync<BackendAuthProfile>(request, HttpStatusCode.OK, cancellationToken)
            is BackendAuthProfile profile && IsValid(profile)
                ? profile.ToClientProfile()
                : throw new AuthenticationSourceUnavailableException("A fonte retornou um perfil incompleto.");
    }

    public async Task<ClientProfile> UpdateAsync(string accountId, ClientProfileUpdate update,
        CancellationToken cancellationToken = default)
    {
        if (update.Phone is not null || update.Position is not null)
            throw new ProfileOperationException("invalid-profile", (int)HttpStatusCode.BadRequest);
        string path = $"api/self-service/accounts/{Uri.EscapeDataString(accountId)}/profile";
        using HttpRequestMessage request = new(HttpMethod.Patch, path)
        {
            Content = JsonContent.Create(new { name = update.Name })
        };
        BackendAuthProfile profile = await SendJsonAsync<BackendAuthProfile>(
            request, HttpStatusCode.OK, cancellationToken);
        if (!IsValid(profile))
            throw new AuthenticationSourceUnavailableException("A fonte retornou um perfil incompleto.");
        return profile.ToClientProfile();
    }

    public async Task ChangePasswordAsync(string accountId, string currentPassword, string newPassword,
        CancellationToken cancellationToken = default)
    {
        string path = $"api/self-service/accounts/{Uri.EscapeDataString(accountId)}/password";
        using HttpRequestMessage request = new(HttpMethod.Post, path)
        {
            Content = JsonContent.Create(new { currentPassword, newPassword })
        };
        await SendNoContentAsync(request, cancellationToken);
    }

    public async Task<ClientPendingEmailChange> RequestEmailChangeAsync(string accountId, string newEmail,
        string currentPassword, CancellationToken cancellationToken = default)
    {
        string path = $"api/self-service/accounts/{Uri.EscapeDataString(accountId)}/email-change";
        using HttpRequestMessage request = new(HttpMethod.Post, path)
        {
            Content = JsonContent.Create(new { newEmail, currentPassword })
        };
        return await SendJsonAsync<ClientPendingEmailChange>(
            request, HttpStatusCode.Accepted, cancellationToken);
    }

    public async Task CancelEmailChangeAsync(string accountId,
        CancellationToken cancellationToken = default)
    {
        string path = $"api/self-service/accounts/{Uri.EscapeDataString(accountId)}/email-change";
        using HttpRequestMessage request = new(HttpMethod.Delete, path);
        await SendNoContentAsync(request, cancellationToken);
    }

    public async Task<EmailChangeConfirmation> ConfirmEmailChangeAsync(string token,
        CancellationToken cancellationToken = default)
    {
        using HttpRequestMessage request = new(HttpMethod.Post, "api/self-service/email-change/confirm")
        {
            Content = JsonContent.Create(new { token })
        };
        return await SendJsonAsync<EmailChangeConfirmation>(
            request, HttpStatusCode.OK, cancellationToken);
    }

    public async Task<bool> IsCurrentAsync(string accountId, long credentialVersion,
        CancellationToken cancellationToken = default)
    {
        BackendSessionState? state = await ReadSessionStateAsync(accountId, cancellationToken);
        return state is not null && IsActiveState(state, accountId)
            && state.CredentialVersion == credentialVersion;
    }

    private async Task<BackendSessionState?> ReadSessionStateAsync(string accountId,
        CancellationToken cancellationToken)
    {
        string path = $"api/self-service/accounts/{Uri.EscapeDataString(accountId)}/session-state";
        using HttpRequestMessage request = new(HttpMethod.Get, path);
        using HttpResponseMessage response = await SendAsync(request, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound
            || response.StatusCode == HttpStatusCode.Unauthorized)
            return null;
        if (response.StatusCode != HttpStatusCode.OK)
            throw SourceFailure(response.StatusCode);
        BackendSessionState? state = await response.Content.ReadFromJsonAsync<BackendSessionState>(cancellationToken);
        if (state is null || string.IsNullOrWhiteSpace(state.Id) || state.CredentialVersion < 0)
            throw new AuthenticationSourceUnavailableException("A fonte retornou estado de sessão inválido.");
        return state;
    }

    private async Task<T> SendJsonAsync<T>(HttpRequestMessage request, HttpStatusCode expected,
        CancellationToken cancellationToken)
    {
        using HttpResponseMessage response = await SendAsync(request, cancellationToken);
        if (response.StatusCode != expected)
            throw await ProfileFailureAsync(response, cancellationToken);
        try
        {
            T? result = await response.Content.ReadFromJsonAsync<T>(cancellationToken);
            return result ?? throw new AuthenticationSourceUnavailableException(
                "A fonte retornou uma resposta vazia.");
        }
        catch (JsonException failure)
        {
            throw SourceFailure(failure);
        }
    }

    private async Task SendNoContentAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        using HttpResponseMessage response = await SendAsync(request, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NoContent)
            return;
        throw await ProfileFailureAsync(response, cancellationToken);
    }

    private async Task<ProfileOperationException> ProfileFailureAsync(
        HttpResponseMessage response, CancellationToken cancellationToken)
    {
        if (response.StatusCode == HttpStatusCode.Unauthorized)
            return new ProfileOperationException("session-expired", (int)HttpStatusCode.Unauthorized);
        if (response.StatusCode == HttpStatusCode.Forbidden)
            return new ProfileOperationException("forbidden", (int)HttpStatusCode.Forbidden);
        if (response.StatusCode == HttpStatusCode.NotFound)
            return new ProfileOperationException("session-expired", (int)HttpStatusCode.Unauthorized);
        if ((int)response.StatusCode >= 500)
            throw SourceFailure(response.StatusCode);
        BackendProblem? problem = null;
        try
        {
            problem = await response.Content.ReadFromJsonAsync<BackendProblem>(cancellationToken);
        }
        catch (JsonException)
        {
            throw SourceFailure(response.StatusCode);
        }

        if (problem?.Code is null)
            throw SourceFailure(response.StatusCode);
        long? retry = response.Headers.RetryAfter?.Delta is TimeSpan delay
            ? Math.Max(1, (long)Math.Ceiling(delay.TotalSeconds))
            : null;
        return new ProfileOperationException(problem.Code, (int)response.StatusCode, retry);
    }

    private async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        AddServiceToken(request);
        try
        {
            return await httpClient.SendAsync(request, cancellationToken);
        }
        catch (HttpRequestException failure)
        {
            throw SourceFailure(failure);
        }
        catch (JsonException failure)
        {
            throw SourceFailure(failure);
        }
        catch (OperationCanceledException failure) when (!cancellationToken.IsCancellationRequested)
        {
            throw SourceFailure(failure);
        }
    }

    private void AddServiceToken(HttpRequestMessage request) =>
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", options.ServiceToken);

    private bool IsValid(BackendAuthUser? user) => user is
        { Role: "CLIENTE", Authorizations.CompanyCnpjs: not null, CredentialVersion: >= 0 }
        && !string.IsNullOrWhiteSpace(user.Id) && !string.IsNullOrWhiteSpace(user.Name)
        && !string.IsNullOrWhiteSpace(user.Email)
        && string.Equals(user.ServiceCode, options.ServiceCode, StringComparison.Ordinal)
        && user.Authorizations.CompanyCnpjs.All(cnpj => cnpj is not null && Regex.IsMatch(cnpj, "^[A-Z0-9]{12}[0-9]{2}$"));

    private static bool IsValid(BackendAuthProfile profile) =>
        !string.IsNullOrWhiteSpace(profile.Name)
        && !string.IsNullOrWhiteSpace(profile.Email)
        && profile.Role == "CLIENTE"
        && profile.EditableFields is not null
        && profile.Actions is not null
        && profile.Companies is not null
        && profile.Companies.All(company => !string.IsNullOrWhiteSpace(company.Id)
            && !string.IsNullOrWhiteSpace(company.Cnpj));

    private static bool IsActiveState(BackendSessionState state, string accountId) =>
        state.Active && state.EmailConfirmed
        && string.Equals(state.Id, accountId, StringComparison.Ordinal)
        && state.CredentialVersion >= 0;

    private static AuthenticationSourceUnavailableException SourceFailure(HttpStatusCode status) =>
        new($"A fonte de autenticação respondeu HTTP {(int)status}.");

    private static AuthenticationSourceUnavailableException SourceFailure(Exception failure) =>
        new("Não foi possível consultar a fonte de autenticação.", failure);

    private sealed record BackendAuthUser(string? Id, string? Name, string? Email, string? Role,
        string? ServiceCode, BackendAuthAuthorizations? Authorizations, long? CredentialVersion);
    private sealed record BackendAuthAuthorizations(IReadOnlyList<string>? CompanyCnpjs);
    private sealed record BackendSessionState(string? Id, bool Active, bool EmailConfirmed, long CredentialVersion);
    private sealed record BackendProblem(string? Code);

    private sealed record BackendAuthProfile(string? Name, string? Email, string? Role,
        bool? Active, bool? EmailConfirmed, IReadOnlyList<string>? EditableFields,
        IReadOnlyList<string>? Actions, IReadOnlyList<BackendAuthCompany>? Companies,
        ClientPendingEmailChange? PendingEmailChange, ClientPasswordPolicy? PasswordPolicy)
    {
        public ClientProfile ToClientProfile() => new(Name!, Email!, Role!, EditableFields!,
            Actions!, Companies!.Select(company => new ClientProfileCompany(company.Id!, null, company.Cnpj)).ToArray(),
            PendingEmailChange, PasswordPolicy, Active, EmailConfirmed);
    }

    private sealed record BackendAuthCompany(string? Id, string? Cnpj);
}
