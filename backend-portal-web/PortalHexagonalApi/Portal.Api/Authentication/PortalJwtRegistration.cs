using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using Portal.Adapters.BackendAuth;
using Portal.Application.Authentication;

namespace Portal.Api.Authentication;

public static class PortalJwtRegistration
{
    public static IServiceCollection AddPortalJwt(this IServiceCollection services, IConfiguration configuration)
    {
        services.AddOptions<PortalJwtOptions>()
            .Bind(configuration.GetSection(PortalJwtOptions.SectionName))
            .Validate(value => !string.IsNullOrWhiteSpace(value.Issuer), "PortalJwt:Issuer é obrigatório.")
            .Validate(value => !string.IsNullOrWhiteSpace(value.Audience), "PortalJwt:Audience é obrigatório.")
            .Validate(PortalJwtOptions.HasValidSigningKey, "PortalJwt:SigningKey deve conter pelo menos 32 bytes aleatórios em Base64.")
            .Validate(value => value.ExpirationSeconds > 0, "PortalJwt:ExpirationSeconds deve ser positivo.")
            .ValidateOnStart();
        services.AddSingleton(TimeProvider.System);
        services.AddSingleton<IPortalSessionIssuer, PortalJwtService>();
        services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer();
        services.AddOptions<JwtBearerOptions>(JwtBearerDefaults.AuthenticationScheme)
            .Configure<IOptions<PortalJwtOptions>, IOptions<BackendAuthOptions>>((bearer, jwtOptions, backendOptions) =>
            {
                PortalJwtOptions jwt = jwtOptions.Value;
                bearer.MapInboundClaims = false;
                bearer.IncludeErrorDetails = false;
                bearer.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuerSigningKey = true,
                    RequireSignedTokens = true,
                    IssuerSigningKey = new SymmetricSecurityKey(Convert.FromBase64String(jwt.SigningKey)),
                    ValidAlgorithms = [SecurityAlgorithms.HmacSha256],
                    ValidateIssuer = true,
                    ValidIssuer = jwt.Issuer,
                    ValidateAudience = true,
                    ValidAudience = jwt.Audience,
                    RequireExpirationTime = true,
                    ValidateLifetime = true,
                    ClockSkew = TimeSpan.Zero,
                    NameClaimType = "nome",
                    RoleClaimType = "roles"
                };
                bearer.Events = new JwtBearerEvents
                {
                    OnTokenValidated = context =>
                    {
                        var user = context.Principal!;
                        if (new[] { "sub", "nome", "email", "roles", "jti", "iat" }
                                .Any(name => string.IsNullOrWhiteSpace(user.FindFirst(name)?.Value))
                            || user.FindFirst("authSource")?.Value != "lactec"
                            || user.FindFirst("serviceCode")?.Value != backendOptions.Value.ServiceCode)
                            context.Fail("Identidade do portal inválida.");
                        return Task.CompletedTask;
                    },
                    OnChallenge = async context =>
                    {
                        context.HandleResponse();
                        context.Response.Headers.WWWAuthenticate = "Bearer";
                        await WriteProblem(context.HttpContext, 401, "invalid-portal-token", "Sessão ausente ou expirada.");
                    },
                    OnForbidden = context => WriteProblem(context.HttpContext, 403, "forbidden", "Sem permissão para esta operação.")
                };
            });
        services.AddAuthorizationBuilder().SetFallbackPolicy(new AuthorizationPolicyBuilder()
            .RequireAuthenticatedUser().RequireRole("CLIENTE").Build());
        return services;
    }

    private static async Task WriteProblem(HttpContext context, int status, string code, string title)
    {
        context.Response.StatusCode = status;
        context.Response.Headers.CacheControl = "no-store";
        await Results.Problem(statusCode: status, title: title,
            type: "https://portal.lactec.org.br/problemas/" + code,
            extensions: new Dictionary<string, object?> { ["code"] = code, ["traceId"] = context.TraceIdentifier })
            .ExecuteAsync(context);
    }
}
