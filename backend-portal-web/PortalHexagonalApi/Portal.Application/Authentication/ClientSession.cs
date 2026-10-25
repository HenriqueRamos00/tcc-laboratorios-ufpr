namespace Portal.Application.Authentication;

public record ClientSession(string AccessToken, string TokenType, long ExpiresIn, ClientUser User)
{
    public override string ToString() => $"ClientSession {{ AccessToken = [REDACTED], User = {User.Id} }}";
}

public record ClientUser(
    string Id, string Name, string Email, string Role, IReadOnlyList<ClientCompany>? Companies,
    string? ServiceCode = null, ClientAuthorizations? Authorizations = null);

public record ClientCompany(string Id, string Name);

public record ClientAuthorizations(IReadOnlyList<string> CompanyCnpjs);
