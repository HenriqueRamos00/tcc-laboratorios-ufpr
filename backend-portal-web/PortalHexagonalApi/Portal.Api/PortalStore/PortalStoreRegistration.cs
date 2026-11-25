using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

using Portal.Adapters.PortalStore;
using Portal.Api.Configuration;
using Portal.Application.Quotes;

namespace Portal.Api.PortalStore;

public static class PortalStoreRegistration
{
    public static IServiceCollection AddPortalStore(this IServiceCollection services, IConfiguration configuration)
    {
        if (configuration[$"{PortalOptions.SectionName}:DataSource"] == PortalOptions.BackendLabSource)
        {
            services.AddScoped<IClientDecisionRepository, NotConnectedClientDecisionRepository>();
            return services;
        }

        string source = configuration[$"{PortalStoreOptions.SectionName}:Source"] is { Length: > 0 } configured
            ? configured
            : PortalStoreOptions.NotConnectedSource;

        switch (source)
        {
            case PortalStoreOptions.NotConnectedSource:
                services.AddScoped<IClientDecisionRepository, NotConnectedClientDecisionRepository>();
                return services;

            case PortalStoreOptions.SqlServerSource:
                services
                    .AddOptions<PortalStoreOptions>()
                    .Bind(configuration.GetSection(PortalStoreOptions.SectionName))
                    .Validate(
                        options => !options.Problems().Any(),
                        $"Configuracao '{PortalStoreOptions.SectionName}' incompleta ou fora dos limites. "
                        + "Confira PortalStore__Server, PortalStore__Database, PortalStore__User e PortalStore__Password.")
                    .ValidateOnStart();

                services.AddDbContext<PortalStoreDbContext>((provider, builder) =>
                {
                    PortalStoreOptions options = provider.GetRequiredService<IOptions<PortalStoreOptions>>().Value;
                    builder.UseSqlServer(
                        options.BuildConnectionString(),
                        sqlServer => sqlServer.CommandTimeout(options.CommandTimeoutSeconds));
                });
                services.AddScoped<IClientDecisionRepository, SqlServerClientDecisionRepository>();
                services.AddHostedService<PortalStoreMigration>();
                return services;

            default:
                throw new InvalidOperationException(
                    $"Configuracao '{PortalStoreOptions.SectionName}:Source' com valor desconhecido '{source}'. "
                    + $"Valores aceitos: '{PortalStoreOptions.NotConnectedSource}', '{PortalStoreOptions.SqlServerSource}'.");
        }
    }
}
