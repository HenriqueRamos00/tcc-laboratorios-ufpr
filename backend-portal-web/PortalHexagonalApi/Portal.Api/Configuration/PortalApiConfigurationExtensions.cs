using System.Globalization;
using System.Security.Claims;
using System.Text;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Mvc;
using Microsoft.IdentityModel.Tokens;
using Portal.Adapters.BackendLab;
using Portal.Api.Models;
using Portal.Api.OpenApi;
using Portal.Api.Security;
using Portal.Application.Internal;
using Portal.Application.Internal.Auth;
using Portal.Application.Internal.Technicians;

namespace Portal.Api.Configuration;

public static class PortalApiConfigurationExtensions
{
    public static IServiceCollection AddPortalApiControllers(this IServiceCollection services)
    {
        services.AddControllers().AddJsonOptions(options =>
            options.JsonSerializerOptions.Converters.Add(new JsonStringEnumConverter(allowIntegerValues: false)));
        services.Configure<ApiBehaviorOptions>(options =>
        {
            options.InvalidModelStateResponseFactory = context =>
            {
                string message = context.ModelState.Values
                    .SelectMany(value => value.Errors)
                    .Select(error => error.ErrorMessage)
                    .FirstOrDefault(value => !string.IsNullOrWhiteSpace(value)) ?? "Requisição inválida.";
                return new BadRequestObjectResult(new { message });
            };
        });

        return services;
    }

    public static IServiceCollection AddBackendLabIntegration(
        this IServiceCollection services,
        IConfiguration configuration,
        IHostEnvironment environment)
    {
        BackendLabOptions backendLabOptions =
            configuration.GetSection(BackendLabOptions.SectionName).Get<BackendLabOptions>() ?? new();
        if (!backendLabOptions.IsValid(environment.IsProduction(), out string backendLabError))
            throw new InvalidOperationException(backendLabError);
        services.AddSingleton(backendLabOptions);

        services.AddHttpContextAccessor();
        services.AddScoped<IBackendLabRequestContext, HttpBackendLabRequestContext>();
        services.AddHttpClient<BackendLabHttpClient>((serviceProvider, client) =>
        {
            BackendLabOptions options = serviceProvider.GetRequiredService<BackendLabOptions>();
            client.BaseAddress = new Uri($"{options.BaseUrl.TrimEnd('/')}/", UriKind.Absolute);
            client.Timeout = TimeSpan.FromSeconds(options.TimeoutSeconds);
        });
        services.AddScoped<IInternalAuthPort>(serviceProvider =>
            serviceProvider.GetRequiredService<BackendLabHttpClient>());
        services.AddScoped<ITechniciansPort>(serviceProvider =>
            serviceProvider.GetRequiredService<BackendLabHttpClient>());
        services.AddScoped<InternalAuthService>();
        services.AddScoped<TechniciansService>();

        return services;
    }

    public static IServiceCollection AddInternalApiSecurity(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        BackendLabJwtOptions jwtOptions =
            configuration.GetSection(BackendLabJwtOptions.SectionName).Get<BackendLabJwtOptions>() ?? new();
        if (!jwtOptions.IsValid(out string jwtError))
            throw new InvalidOperationException(jwtError);
        byte[] jwtSecret = Encoding.UTF8.GetBytes(jwtOptions.Secret);

        services
            .AddAuthentication()
            .AddJwtBearer(InternalSecurity.Scheme, options =>
            {
                options.MapInboundClaims = false;
                options.RequireHttpsMetadata = false;
                options.TokenValidationParameters = new TokenValidationParameters
                {
                    ValidateIssuerSigningKey = true,
                    IssuerSigningKey = new SymmetricSecurityKey(jwtSecret),
                    ValidateIssuer = true,
                    ValidIssuer = jwtOptions.Issuer,
                    ValidateAudience = true,
                    ValidAudience = jwtOptions.Audience,
                    ValidateLifetime = true,
                    RequireExpirationTime = true,
                    RequireSignedTokens = true,
                    ValidAlgorithms = [SecurityAlgorithms.HmacSha256],
                    ClockSkew = TimeSpan.FromSeconds(jwtOptions.ClockSkewSeconds),
                    NameClaimType = "sub",
                    RoleClaimType = "role"
                };
                options.Events = new JwtBearerEvents
                {
                    OnTokenValidated = context =>
                    {
                        ClaimsPrincipal? principal = context.Principal;
                        string? subject = principal?.FindFirst("sub")?.Value;
                        string? userId = principal?.FindFirst("userId")?.Value;
                        string? role = principal?.FindFirst("role")?.Value;
                        string? issuedAt = principal?.FindFirst("iat")?.Value;
                        bool hasIssuedAt = long.TryParse(
                            issuedAt,
                            NumberStyles.None,
                            CultureInfo.InvariantCulture,
                            out long issuedAtSeconds);
                        long latestAllowedIssuedAt =
                            DateTimeOffset.UtcNow.ToUnixTimeSeconds() + jwtOptions.ClockSkewSeconds;

                        if (string.IsNullOrWhiteSpace(subject) ||
                            !long.TryParse(userId, NumberStyles.None, CultureInfo.InvariantCulture, out long id) || id <= 0 ||
                            role is not ("ADMIN" or "TECNICO") ||
                            !hasIssuedAt || issuedAtSeconds > latestAllowedIssuedAt)
                        {
                            context.Fail("O token interno não contém as declarações obrigatórias.");
                        }

                        return Task.CompletedTask;
                    },
                    OnChallenge = async context =>
                    {
                        context.HandleResponse();
                        context.Response.Headers.WWWAuthenticate = "Bearer";
                        context.Response.StatusCode = StatusCodes.Status401Unauthorized;
                        await context.Response.WriteAsJsonAsync(
                            new { message = "Não autenticado." },
                            cancellationToken: context.HttpContext.RequestAborted);
                    },
                    OnForbidden = async context =>
                    {
                        context.Response.StatusCode = StatusCodes.Status403Forbidden;
                        await context.Response.WriteAsJsonAsync(
                            new { message = "Sem permissão." },
                            cancellationToken: context.HttpContext.RequestAborted);
                    }
                };
            });

        services.AddAuthorizationBuilder()
            .AddPolicy(InternalSecurity.AdminPolicy, policy =>
            {
                policy.AddAuthenticationSchemes(InternalSecurity.Scheme);
                policy.RequireAuthenticatedUser();
                policy.RequireClaim("role", "ADMIN");
            });

        return services;
    }

    public static IServiceCollection AddPortalOpenApi(this IServiceCollection services)
    {
        services.AddEndpointsApiExplorer();
        services.AddOpenApi(options =>
        {
            options.AddDocumentTransformer<InternalBearerDocumentTransformer>();
            options.AddOperationTransformer<InternalBearerOperationTransformer>();
        });

        return services;
    }
}
