namespace Portal.Application.Internal.Auth;

public sealed record InternalLoginResponse(
    string AccessToken,
    string TokenType,
    long ExpiresIn,
    InternalUser User);

public sealed record InternalUser(long Id, string Name, string Email, string Role);
