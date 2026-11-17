using System.IdentityModel.Tokens.Jwt;
using System.Net.Http.Headers;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.IdentityModel.Tokens;

namespace Portal.Tests.Authentication;

/// <summary>
/// Sessoes do portal para a suite: o mesmo JWT que o login emite, assinado com a chave de
/// teste, com os CNPJs que o teste quiser. E por aqui que um teste vira "o cliente A" ou "o
/// cliente B" sem passar pelo Backend Auth.
/// </summary>
internal static class PortalSessions
{
    public const string Issuer = "portal-test";
    public const string Audience = "portal-web-test";
    public const string ServiceCode = "portal-web";

    public static readonly string SigningKey = Convert.ToBase64String(Enumerable.Repeat((byte)42, 64).ToArray());
    public static readonly string ServiceToken = "svc_" + new string('a', 43);

    public static IWebHostBuilder UseLactecSessions(this IWebHostBuilder builder)
    {
        builder.UseSetting("Portal:DataSource", "lactec");
        builder.UseSetting("Portal:UseDemoData", "true");
        builder.UseSetting("BackendLab:BaseUrl", "");
        builder.UseSetting("BackendAuth:BaseUrl", "https://backend-auth.test/");
        builder.UseSetting("BackendAuth:ServiceToken", ServiceToken);
        builder.UseSetting("BackendAuth:ServiceCode", ServiceCode);
        builder.UseSetting("PortalJwt:Issuer", Issuer);
        builder.UseSetting("PortalJwt:Audience", Audience);
        builder.UseSetting("PortalJwt:SigningKey", SigningKey);

        return builder;
    }

    public static string TokenOf(params string[] companyCnpjs)
    {
        return TokenWithClaim(companyCnpjs);
    }

    public static string TokenWithClaim(object? companyCnpjs)
    {
        Dictionary<string, object> claims = new()
        {
            ["sub"] = "42",
            ["nome"] = "Cliente de Teste",
            ["email"] = "cliente@example.test",
            ["roles"] = "CLIENTE",
            ["jti"] = Guid.NewGuid().ToString("N"),
            ["authSource"] = "lactec",
            ["serviceCode"] = ServiceCode
        };

        if (companyCnpjs is not null) claims["companyCnpjs"] = companyCnpjs;

        DateTime now = DateTime.UtcNow;
        JwtSecurityTokenHandler handler = new();

        return handler.WriteToken(handler.CreateToken(new SecurityTokenDescriptor
        {
            Claims = claims,
            Issuer = Issuer,
            Audience = Audience,
            IssuedAt = now.AddMinutes(-1),
            NotBefore = now.AddMinutes(-1),
            Expires = now.AddMinutes(15),
            SigningCredentials = new SigningCredentials(
                new SymmetricSecurityKey(Convert.FromBase64String(SigningKey)), SecurityAlgorithms.HmacSha256)
        }));
    }

    public static HttpClient CreateClientOf(this WebApplicationFactory<Program> portal, params string[] companyCnpjs)
    {
        return portal.CreateClientWithToken(TokenOf(companyCnpjs));
    }

    public static HttpClient CreateClientWithToken(this WebApplicationFactory<Program> portal, string? token)
    {
        HttpClient client = portal.CreateClient();

        client.DefaultRequestHeaders.Authorization = token is null
            ? null
            : new AuthenticationHeaderValue("Bearer", token);

        return client;
    }
}
