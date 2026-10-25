using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Portal.Application.Authentication;

namespace Portal.Api.Authentication;

public sealed class PortalJwtService(IOptions<PortalJwtOptions> options, TimeProvider time) : IPortalSessionIssuer
{
    public ClientSession Issue(ClientUser user)
    {
        PortalJwtOptions settings = options.Value;
        DateTime issuedAt = time.GetUtcNow().UtcDateTime;
        SecurityTokenDescriptor descriptor = new()
        {
            Issuer = settings.Issuer,
            Audience = settings.Audience,
            IssuedAt = issuedAt,
            NotBefore = issuedAt,
            Expires = issuedAt.AddSeconds(settings.ExpirationSeconds),
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
                ["jti"] = Guid.NewGuid().ToString("N")
            }
        };
        JwtSecurityTokenHandler handler = new();
        string token = handler.WriteToken(handler.CreateToken(descriptor));
        return new ClientSession(token, "Bearer", settings.ExpirationSeconds, user);
    }

    public static ClientUser ReadUser(ClaimsPrincipal user) => new(
        user.FindFirstValue("sub")!, user.FindFirstValue("nome")!, user.FindFirstValue("email")!,
        user.FindFirstValue("roles")!, null, user.FindFirstValue("serviceCode"),
        new ClientAuthorizations(user.FindAll("companyCnpjs").Select(claim => claim.Value).Order(StringComparer.Ordinal).ToArray()));
}
