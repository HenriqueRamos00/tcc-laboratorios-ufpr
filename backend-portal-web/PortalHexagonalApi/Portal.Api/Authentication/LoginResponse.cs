using System.Text.Json.Serialization;

using Portal.Application.Authentication;

namespace Portal.Api.Authentication;

public sealed record LoginResponse(
    [property: JsonPropertyName("Token")] string Token,
    string TokenType,
    long ExpiresIn,
    ClientUser User);
