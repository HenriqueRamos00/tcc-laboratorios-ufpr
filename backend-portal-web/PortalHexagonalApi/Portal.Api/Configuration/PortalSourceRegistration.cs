using Microsoft.Extensions.Options;

using Portal.Adapters.Authentication;
using Portal.Adapters.BackendLab;
using Portal.Application.Authentication;

namespace Portal.Api.Configuration;

public static class PortalSourceRegistration
{
    public static IServiceCollection AddPortalDataSource(
        this IServiceCollection services, IConfiguration configuration)
    {
        PortalOptions portal = configuration.GetSection(PortalOptions.SectionName).Get<PortalOptions>()
            ?? new PortalOptions();

        if (string.IsNullOrWhiteSpace(portal.DataSource))
            throw new InvalidOperationException("Configuração 'Portal:DataSource' não definida.");

        switch (portal.DataSource)
        {
            case PortalOptions.BackendLabSource:
                services.AddOptions<BackendLabOptions>()
                    .Bind(configuration.GetSection(BackendLabOptions.SectionName))
                    .Validate(options => Uri.TryCreate(options.BaseUrl, UriKind.Absolute, out Uri? uri)
                        && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps)
                        && string.IsNullOrEmpty(uri.UserInfo)
                        && string.IsNullOrEmpty(uri.Query)
                        && string.IsNullOrEmpty(uri.Fragment),
                        "BackendLab:BaseUrl é obrigatório e deve ser uma URL HTTP ou HTTPS sem credenciais, query ou fragmento.")
                    .ValidateOnStart();
                services.AddHttpClient<IClientAuthenticator, BackendLabAdapter>((provider, client) =>
                    {
                        BackendLabOptions backendLab = provider.GetRequiredService<IOptions<BackendLabOptions>>().Value;
                        client.BaseAddress = new Uri(backendLab.BaseUrl.TrimEnd('/') + "/");
                        client.Timeout = TimeSpan.FromSeconds(10);
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false });
                return services;

            case PortalOptions.LactecSource:
                services.AddScoped<IClientAuthenticator, UnavailableClientAuthenticator>();
                return services;

            default:
                throw new InvalidOperationException(
                    $"Configuração 'Portal:DataSource' com valor desconhecido '{portal.DataSource}'.");
        }
    }
}
