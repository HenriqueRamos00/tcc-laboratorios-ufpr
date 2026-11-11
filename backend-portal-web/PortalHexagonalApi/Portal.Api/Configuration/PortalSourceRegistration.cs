using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;

using Portal.Adapters.BackendAuth;
using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
using Portal.Api.Quotes;
using Portal.Application.Quotes;
using Portal.Api.Authentication;
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

        services.AddSingleton(portal);

        switch (portal.DataSource)
        {
            case PortalOptions.BackendLabSource:
                services.AddHttpContextAccessor();
                services.AddTransient<SpringBearerForwardingHandler>();
                services.AddScoped<SpringQuoteAuthenticationFilter>();
                services.Configure<MvcOptions>(options => options.Filters.AddService<SpringQuoteAuthenticationFilter>());
                services.AddOptions<BackendLabOptions>()
                    .Bind(configuration.GetSection(BackendLabOptions.SectionName))
                    .Validate(options => Uri.TryCreate(options.BaseUrl, UriKind.Absolute, out Uri? uri)
                        && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps)
                        && string.IsNullOrEmpty(uri.UserInfo)
                        && string.IsNullOrEmpty(uri.Query)
                        && string.IsNullOrEmpty(uri.Fragment),
                        "BackendLab:BaseUrl é obrigatório e deve ser uma URL HTTP ou HTTPS sem credenciais, query ou fragmento.")
                    .ValidateOnStart();
                services.AddHttpClient<IQuoteRepository, SpringQuoteRepository>((provider, client) =>
                    {
                        BackendLabOptions backendLab = provider.GetRequiredService<IOptions<BackendLabOptions>>().Value;
                        client.BaseAddress = new Uri(backendLab.BaseUrl.TrimEnd('/') + "/");
                        client.Timeout = TimeSpan.FromSeconds(10);
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false })
                    .AddHttpMessageHandler<SpringBearerForwardingHandler>();
                services.AddHttpClient<IQuoteDocumentRepository, SpringQuoteDocumentRepository>((provider, client) =>
                    {
                        BackendLabOptions backendLab = provider.GetRequiredService<IOptions<BackendLabOptions>>().Value;
                        client.BaseAddress = new Uri(backendLab.BaseUrl.TrimEnd('/') + "/");
                        client.Timeout = TimeSpan.FromSeconds(10);
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false })
                    .AddHttpMessageHandler<SpringBearerForwardingHandler>();
                services.AddHttpClient<IClientAuthenticator, BackendLabAdapter>((provider, client) =>
                    {
                        BackendLabOptions backendLab = provider.GetRequiredService<IOptions<BackendLabOptions>>().Value;
                        client.BaseAddress = new Uri(backendLab.BaseUrl.TrimEnd('/') + "/");
                        client.Timeout = TimeSpan.FromSeconds(10);
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false });
                return services;

            case PortalOptions.LactecSource:
                services.AddHttpContextAccessor();
                services.AddScoped<IQuoteCompanyScope, AuthenticatedQuoteCompanyScope>();
                // A proposta escrita a mao continua valendo; o orcamento sem arquivo recebe a pagina
                // gerada a partir dele mesmo, para o botao de baixar nunca cair no vazio na demonstracao.
                services.AddScoped<IQuoteDocumentRepository>(provider =>
                    new GeneratedQuoteDocumentRepository(
                        provider.GetRequiredService<IQuoteRepository>(),
                        new LocalQuoteDocumentRepository()));
                if (portal.UseDemoData)
                    services.AddScoped<IQuoteRepository>(provider =>
                        new FakeSalesforceQuoteRepository(provider.GetRequiredService<IQuoteCompanyScope>()));
                else
                    services.AddSalesforceQuotes(configuration);
                services.AddOptions<BackendAuthOptions>()
                    .Bind(configuration.GetSection(BackendAuthOptions.SectionName))
                    .Validate(value => Uri.TryCreate(value.BaseUrl, UriKind.Absolute, out Uri? uri)
                        && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps)
                        && string.IsNullOrEmpty(uri.UserInfo) && string.IsNullOrEmpty(uri.Query) && string.IsNullOrEmpty(uri.Fragment),
                        "BackendAuth:BaseUrl é obrigatório e deve ser uma URL HTTP ou HTTPS sem credenciais, query ou fragmento.")
                    .Validate(value => !string.IsNullOrEmpty(value.ServiceToken) && Regex.IsMatch(value.ServiceToken, "^svc_[A-Za-z0-9_-]{43}$"),
                        "BackendAuth:ServiceToken deve ser um token de serviço emitido pelo Backend Auth.")
                    .Validate(value => !string.IsNullOrEmpty(value.ServiceCode) && Regex.IsMatch(value.ServiceCode, "^[a-z0-9]+(?:-[a-z0-9]+)*$"),
                        "BackendAuth:ServiceCode é obrigatório e deve corresponder ao serviço autorizado.")
                    .ValidateOnStart();
                services.AddSingleton(provider => provider.GetRequiredService<IOptions<BackendAuthOptions>>().Value);
                services.AddHttpClient<IClientAuthenticator, BackendAuthAdapter>((provider, client) =>
                    {
                        BackendAuthOptions backend = provider.GetRequiredService<IOptions<BackendAuthOptions>>().Value;
                        client.BaseAddress = new Uri(backend.BaseUrl.TrimEnd('/') + "/");
                        client.Timeout = TimeSpan.FromSeconds(10);
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false });
                services.AddPortalJwt(configuration);
                return services;

            default:
                throw new InvalidOperationException(
                    $"Configuração 'Portal:DataSource' com valor desconhecido '{portal.DataSource}'.");
        }
    }

    /// <summary>
    /// O Salesforce so existe para <c>lactec</c> sem dados de demonstracao, e por isso as
    /// credenciais so sao exigidas nesse caso. Registradas no Program, elas eram lidas em
    /// todas as composicoes e a falta delas so aparecia na primeira consulta, como 502.
    /// </summary>
    private static void AddSalesforceQuotes(this IServiceCollection services, IConfiguration configuration)
    {
        services.AddOptions<SalesforceOptions>()
            .Bind(configuration.GetSection(SalesforceOptions.SectionName))
            .Validate(value => Uri.TryCreate(value.Url, UriKind.Absolute, out Uri? uri)
                && (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps),
                "Salesforce:Url é obrigatório e deve ser uma URL HTTP ou HTTPS absoluta.")
            .Validate(value => !string.IsNullOrWhiteSpace(value.GrantType),
                "Salesforce:GrantType é obrigatório.")
            .Validate(value => !string.IsNullOrWhiteSpace(value.ClientId),
                "Salesforce:ClientId é obrigatório.")
            .Validate(value => !string.IsNullOrWhiteSpace(value.ClientSecret),
                "Salesforce:ClientSecret é obrigatório.")
            .ValidateOnStart();
        services.AddSingleton(provider => provider.GetRequiredService<IOptions<SalesforceOptions>>().Value);
        services.AddHttpClient<ISalesforceTokenClient, SalesforceTokenClient>();
        services.AddSingleton<ISalesforceTokenProvider, SalesforceTokenProvider>();
        services.AddHttpClient<IQuoteRepository, SalesforceQuoteRepository>(client =>
                client.Timeout = TimeSpan.FromSeconds(10))
            .ConfigurePrimaryHttpMessageHandler(() => new HttpClientHandler { AllowAutoRedirect = false });
    }
}
