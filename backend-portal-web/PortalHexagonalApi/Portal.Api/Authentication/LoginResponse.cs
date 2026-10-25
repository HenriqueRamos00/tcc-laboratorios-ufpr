using System.Text.Json.Serialization;

namespace Portal.Api.Authentication;

public sealed record LoginResponse(
    [property: JsonPropertyName("Token")] string Token,
    string TokenType,
    long ExpiresIn,
    ClientUserResponse User)
{
    public override string ToString() => "LoginResponse { Token = [REDACTED] }";
}
