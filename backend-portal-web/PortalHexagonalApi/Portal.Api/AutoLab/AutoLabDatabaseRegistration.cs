using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

using Portal.Adapters.AutoLab;

namespace Portal.Api.AutoLab;

public static class AutoLabDatabaseRegistration
{
    public static IServiceCollection AddAutoLabDatabase(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        if (services.Any(descriptor => descriptor.ServiceType == typeof(AutoLabQueryLimits)))
            return services;

        services
            .AddOptions<AutoLabOptions>()
            .Bind(configuration.GetSection(AutoLabOptions.SectionName))
            .Validate(
                options => !options.Problems().Any(),
                $"Configuracao '{AutoLabOptions.SectionName}' incompleta ou fora dos limites. "
                + "Confira AutoLab__Server, AutoLab__Database, AutoLab__User, AutoLab__Password e os limites.")
            .ValidateOnStart();

        services.AddSingleton(provider =>
        {
            AutoLabOptions options = provider.GetRequiredService<IOptions<AutoLabOptions>>().Value;

            return new AutoLabQueryLimits(options.MaxRowsPerQuery);
        });

        services.AddDbContextPool<AutoLabDbContext>((provider, builder) =>
        {
            AutoLabOptions options = provider.GetRequiredService<IOptions<AutoLabOptions>>().Value;

            builder
                .UseSqlServer(
                    options.BuildConnectionString(),
                    sqlServer =>
                    {
                        sqlServer.CommandTimeout(options.CommandTimeoutSeconds);

                        if (options.MaxRetryCount > 0)
                            sqlServer.EnableRetryOnFailure(
                                options.MaxRetryCount,
                                TimeSpan.FromSeconds(options.MaxRetryDelaySeconds),
                                errorNumbersToAdd: null);
                    })
                .UseQueryTrackingBehavior(QueryTrackingBehavior.NoTracking);
        });

        return services;
    }
}
