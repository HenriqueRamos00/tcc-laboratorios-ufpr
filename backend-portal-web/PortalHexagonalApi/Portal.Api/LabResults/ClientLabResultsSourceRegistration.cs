using Microsoft.Extensions.Options;

using Portal.Adapters.BackendLab;
using Portal.Api.Configuration;
using Portal.Application.LabResults;

namespace Portal.Api.LabResults;

public static class ClientLabResultsSourceRegistration
{
    public static IServiceCollection AddClientLabResultsSource(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        PortalOptions portal = configuration.GetSection(PortalOptions.SectionName).Get<PortalOptions>()
            ?? new PortalOptions();

        if (portal.DataSource == PortalOptions.BackendLabSource)
        {
            services.AddHttpContextAccessor();
            services.AddTransient<SpringBearerForwardingHandler>();
            services.AddHttpClient<IClientLabResultsRepository, BackendLabClientLabResultsRepository>((provider, client) =>
                {
                    BackendLabOptions backendLab = provider.GetRequiredService<IOptions<BackendLabOptions>>().Value;
                    client.BaseAddress = new Uri(backendLab.BaseUrl.TrimEnd('/') + "/");
                    client.Timeout = TimeSpan.FromSeconds(10);
                })
                .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false })
                .AddHttpMessageHandler<SpringBearerForwardingHandler>();
        }
        else
        {
            services.AddScoped<IClientLabResultsRepository, NotConnectedClientLabResultsRepository>();
        }

        services.AddScoped<IForConsultingClientLabResults, ClientLabResultsConsultation>();
        return services;
    }
}
