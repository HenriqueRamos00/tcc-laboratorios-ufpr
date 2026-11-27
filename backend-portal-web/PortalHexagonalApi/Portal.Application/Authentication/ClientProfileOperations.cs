using System.Text.Json.Serialization;

namespace Portal.Application.Authentication;

public sealed record ClientProfile(
    string Name,
    string Email,
    string Role,
    IReadOnlyList<string> EditableFields,
    IReadOnlyList<string> Actions,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] IReadOnlyList<ClientProfileCompany>? Companies = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] ClientPendingEmailChange? PendingEmailChange = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] ClientPasswordPolicy? PasswordPolicy = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] bool? Active = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] bool? EmailConfirmed = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Phone = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Position = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Unit = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Specialty = null);

public sealed record ClientProfileCompany(string Id,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Name = null,
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)] string? Cnpj = null);

public sealed record ClientPendingEmailChange(
    string NewEmail,
    DateTimeOffset ExpiresAt,
    bool CurrentEmailConfirmed,
    bool NewEmailConfirmed);

public sealed record ClientPasswordPolicy(
    int MinCodePoints,
    int MaxCharacters,
    int? MaxUtf8Bytes,
    bool RequiresUppercase,
    bool RequiresLowercase,
    bool RequiresDigit,
    bool RejectsBlank);

public sealed record ClientProfileUpdate(string Name, string? Phone, string? Position);

public sealed record ClientSessionState(string Id, bool Active, bool EmailConfirmed, long CredentialVersion);

public sealed record EmailChangeConfirmation(string Status);

public interface IClientProfileOperations
{
    Task<ClientProfile> GetAsync(string accountId, CancellationToken cancellationToken = default);

    Task<ClientProfile> UpdateAsync(string accountId, ClientProfileUpdate update,
        CancellationToken cancellationToken = default);

    Task ChangePasswordAsync(string accountId, string currentPassword, string newPassword,
        CancellationToken cancellationToken = default);

    Task<ClientPendingEmailChange> RequestEmailChangeAsync(string accountId, string newEmail,
        string currentPassword, CancellationToken cancellationToken = default);

    Task CancelEmailChangeAsync(string accountId, CancellationToken cancellationToken = default);

    Task<EmailChangeConfirmation> ConfirmEmailChangeAsync(string token,
        CancellationToken cancellationToken = default);
}

public interface IClientSessionStateVerifier
{
    Task<bool> IsCurrentAsync(string accountId, long credentialVersion,
        CancellationToken cancellationToken = default);
}

public sealed class ProfileOperationException(
    string code, int statusCode, long? retryAfterSeconds = null) : Exception(code)
{
    public string Code { get; } = code;
    public int StatusCode { get; } = statusCode;
    public long? RetryAfterSeconds { get; } = retryAfterSeconds;
}
