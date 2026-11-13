using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.JsonWebTokens;
using Microsoft.IdentityModel.Tokens;
using Portal.Application.Authentication;

namespace Portal.Api.Authentication;

public sealed class PortalJwtService(IOptions<PortalJwtOptions> options, TimeProvider time) : IPortalSessionIssuer
{
    public const string SessionStartClaim = "auth_time";

    private static readonly string[] RequiredClaims = ["sub", "nome", "email", "roles", "jti", "iat"];

    public ClientSession Issue(ClientUser user)
    {
        DateTimeOffset now = time.GetUtcNow();

        return IssueWithin(user, now, now)
            ?? throw new InvalidOperationException("A sessão recém-iniciada deveria estar dentro do limite.");
    }

    public async Task<ClientSession?> RenewAsync(string token, string serviceCode)
    {
        PortalJwtOptions settings = options.Value;
        DateTimeOffset now = time.GetUtcNow();

        TokenValidationParameters parameters = ValidationParameters(settings);
        parameters.LifetimeValidator = (notBefore, expires, _, _) =>
            notBefore is not null && expires is not null
            && notBefore.Value <= now.UtcDateTime
            && now.UtcDateTime < expires.Value.AddSeconds(settings.RenewalWindowSeconds);

        TokenValidationResult result = await new JsonWebTokenHandler().ValidateTokenAsync(token, parameters);

        if (!result.IsValid) return null;

        ClaimsPrincipal principal = new(result.ClaimsIdentity);

        if (!HasPortalIdentity(principal, serviceCode)
            || !long.TryParse(principal.FindFirstValue(SessionStartClaim), out long startedAt))
            return null;

        return IssueWithin(ReadUser(principal), DateTimeOffset.FromUnixTimeSeconds(startedAt), now);
    }

    public static TokenValidationParameters ValidationParameters(PortalJwtOptions settings) => new()
    {
        ValidateIssuerSigningKey = true,
        RequireSignedTokens = true,
        IssuerSigningKey = new SymmetricSecurityKey(Convert.FromBase64String(settings.SigningKey)),
        ValidAlgorithms = [SecurityAlgorithms.HmacSha256],
        ValidateIssuer = true,
        ValidIssuer = settings.Issuer,
        ValidateAudience = true,
        ValidAudience = settings.Audience,
        RequireExpirationTime = true,
        ValidateLifetime = true,
        ClockSkew = TimeSpan.Zero,
        NameClaimType = "nome",
        RoleClaimType = "roles"
    };

    public static bool HasPortalIdentity(ClaimsPrincipal user, string serviceCode)
    {
        return !RequiredClaims.Any(name => string.IsNullOrWhiteSpace(user.FindFirst(name)?.Value))
            && user.FindFirst("authSource")?.Value == "lactec"
            && user.FindFirst("serviceCode")?.Value == serviceCode;
    }

    public static ClientUser ReadUser(ClaimsPrincipal user) => new(
        user.FindFirstValue("sub")!, user.FindFirstValue("nome")!, user.FindFirstValue("email")!,
        user.FindFirstValue("roles")!, null, user.FindFirstValue("serviceCode"),
        new ClientAuthorizations(user.FindAll("companyCnpjs").Select(claim => claim.Value).Order(StringComparer.Ordinal).ToArray()));

    private ClientSession? IssueWithin(ClientUser user, DateTimeOffset sessionStart, DateTimeOffset now)
    {
        PortalJwtOptions settings = options.Value;
        DateTimeOffset sessionEnd = sessionStart.AddSeconds(settings.MaxSessionSeconds);

        if (sessionStart > now || now >= sessionEnd) return null;

        DateTimeOffset expires = now.AddSeconds(settings.ExpirationSeconds);
        if (expires > sessionEnd) expires = sessionEnd;

        long expiresIn = (long)(expires - now).TotalSeconds;
        if (expiresIn <= 0) return null;

        SecurityTokenDescriptor descriptor = new()
        {
            Issuer = settings.Issuer,
            Audience = settings.Audience,
            IssuedAt = now.UtcDateTime,
            NotBefore = now.UtcDateTime,
            Expires = now.AddSeconds(expiresIn).UtcDateTime,
            SigningCredentials = new SigningCredentials(
                new SymmetricSecurityKey(Convert.FromBase64String(settings.SigningKey)), SecurityAlgorithms.HmacSha256),
            Claims = new Dictionary<string, object>
            {
                ["sub"] = user.Id,
                ["nome"] = user.Name,
                ["email"] = user.Email,
                ["roles"] = user.Role,
                ["authSource"] = "lactec",
                ["serviceCode"] = user.ServiceCode!,
                ["companyCnpjs"] = user.Authorizations!.CompanyCnpjs.ToArray(),
                [SessionStartClaim] = sessionStart.ToUnixTimeSeconds(),
                ["jti"] = Guid.NewGuid().ToString("N")
            }
        };
        JwtSecurityTokenHandler handler = new();
        string token = handler.WriteToken(handler.CreateToken(descriptor));
        return new ClientSession(token, "Bearer", expiresIn, user);
    }
}
