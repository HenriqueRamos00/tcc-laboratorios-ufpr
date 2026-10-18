namespace Portal.Application.Authentication;

public record ClientSession(string AccessToken, string TokenType, long ExpiresIn, ClientUser User);

public record ClientUser(
    string Id, string Name, string Email, string Role, IReadOnlyList<ClientCompany> Companies);

public record ClientCompany(string Id, string Name);
