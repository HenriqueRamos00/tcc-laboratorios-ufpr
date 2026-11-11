using Microsoft.Extensions.Options;

using Portal.Adapters.Reports;
using Portal.Adapters.BackendLab;
using Portal.Application.Reports;
using Portal.Api.Configuration;

namespace Portal.Api.Reports;

public static class ReportDocumentSourceRegistration
{
    public static IServiceCollection AddReportDocumentRepository(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        PortalOptions portal = configuration.GetSection(PortalOptions.SectionName).Get<PortalOptions>()
            ?? new PortalOptions();

        switch (portal.DataSource)
        {
            case PortalOptions.BackendLabSource:
                services.AddHttpContextAccessor();
                services.AddTransient<SpringBearerForwardingHandler>();
                services.AddHttpClient<IReportDocumentRepository, SpringReportDocumentRepository>((provider, client) =>
                    {
                        BackendLabOptions backendLab = provider.GetRequiredService<IOptions<BackendLabOptions>>().Value;
                        client.BaseAddress = new Uri(backendLab.BaseUrl.TrimEnd('/') + "/");
                        client.Timeout = TimeSpan.FromSeconds(10);
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false })
                    .AddHttpMessageHandler<SpringBearerForwardingHandler>();
                return services;

            case PortalOptions.LactecSource:
                // Os exemplos documentais são legados do Portal e não dependem da fonte de propostas.
                // Trocar pelo adapter real somente após validar a integração AutoLAB: issue #86.
                services.AddScoped<IReportDocumentRepository, LactecDemoReportDocumentRepository>();
                return services;

            default:
                throw new InvalidOperationException(
                    "Configuração 'Portal:DataSource' inválida para a fonte de relatórios.");
        }
    }
}
