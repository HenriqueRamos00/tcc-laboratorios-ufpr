using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using Portal.Adapters.BackendAuth;
using Portal.Adapters.BackendLab;
using Portal.Adapters.Quotes;
using Portal.Api.Configuration;
using Portal.Application.Authentication;
using Portal.Application.Quotes;

using Xunit;

namespace Portal.Tests.Authentication;

public sealed class PortalSourceRegistrationTests
{
    [Fact]
    public void BackendLabRegistraOAdapterDeAutenticacao()
    {
        using ServiceProvider provider = Register("backend-lab").BuildServiceProvider();

        Assert.IsType<BackendLabAdapter>(provider.GetRequiredService<IClientAuthenticator>());
    }

    [Fact]
    public void BackendLabRegistraAdaptersDeOrcamentosDoSpring()
    {
        using ServiceProvider provider = Register("backend-lab").BuildServiceProvider();
        using IServiceScope scope = provider.CreateScope();

        Assert.IsType<SpringQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
        Assert.IsType<SpringQuoteDocumentRepository>(scope.ServiceProvider.GetRequiredService<IQuoteDocumentRepository>());
    }

    [Fact]
    public void BackendLabNaoSelecionaFixturesDeOrcamentoComDemoHabilitada()
    {
        using ServiceProvider provider = Register("backend-lab", useDemoData: true).BuildServiceProvider();
        using IServiceScope scope = provider.CreateScope();

        Assert.IsType<SpringQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
        Assert.IsType<SpringQuoteDocumentRepository>(scope.ServiceProvider.GetRequiredService<IQuoteDocumentRepository>());
    }

    [Fact]
    public void FiltroDeBearerEhRegistradoSomenteParaFonteBackendLab()
    {
        using ServiceProvider backendLab = Register("backend-lab").BuildServiceProvider();
        using ServiceProvider lactec = Register("lactec").BuildServiceProvider();

        Assert.Contains(backendLab.GetRequiredService<IOptions<MvcOptions>>().Value.Filters
            .OfType<ServiceFilterAttribute>(), filter => filter.ServiceType == typeof(SpringQuoteAuthenticationFilter));
        Assert.DoesNotContain(lactec.GetRequiredService<IOptions<MvcOptions>>().Value.Filters
            .OfType<ServiceFilterAttribute>(), filter => filter.ServiceType == typeof(SpringQuoteAuthenticationFilter));
    }

    [Fact]
    public void LactecPreservaRepositorioEGeracaoDeDocumentoExistentes()
    {
        IServiceCollection services = Register("lactec");
        services.AddScoped<IQuoteRepository, FakeSalesforceQuoteRepository>();
        using ServiceProvider provider = services.BuildServiceProvider();
        using IServiceScope scope = provider.CreateScope();

        Assert.IsType<GeneratedQuoteDocumentRepository>(scope.ServiceProvider.GetRequiredService<IQuoteDocumentRepository>());
    }

    [Fact]
    public void LactecRegistraBackendAuthSemEnderecoDoBackendLab()
    {
        using ServiceProvider provider = Register("lactec", null).BuildServiceProvider();

        Assert.IsType<BackendAuthAdapter>(provider.GetRequiredService<IClientAuthenticator>());
    }

    [Fact]
    public void FonteDesconhecidaInformaOValorConfigurado()
    {
        InvalidOperationException failure = Assert.Throws<InvalidOperationException>(() => Register("outra-fonte"));

        Assert.Contains("outra-fonte", failure.Message, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    public void FonteAusenteOuVaziaRecusaORegistro(string? source)
    {
        Assert.Throws<InvalidOperationException>(() => Register(source));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("endereco-invalido")]
    public void EnderecoAusenteOuInvalidoImpedeUsarOAdapter(string? baseUrl)
    {
        using ServiceProvider provider = Register("backend-lab", baseUrl).BuildServiceProvider();

        OptionsValidationException failure = Assert.Throws<OptionsValidationException>(
            () => provider.GetRequiredService<IClientAuthenticator>());

        Assert.Contains("BackendLab:BaseUrl", failure.Message, StringComparison.Ordinal);
    }

    private static IServiceCollection Register(
        string? source, string? baseUrl = "https://backend-lab.test/", bool useDemoData = false)
    {
        Dictionary<string, string?> values = [];
        if (source is not null) values["Portal:DataSource"] = source;
        values["Portal:UseDemoData"] = useDemoData.ToString();
        if (baseUrl is not null) values["BackendLab:BaseUrl"] = baseUrl;

        if (source == "lactec")
        {
            values["BackendAuth:BaseUrl"] = "https://backend-auth.test/";
            values["BackendAuth:ServiceToken"] = "svc_" + new string('a', 43);
            values["BackendAuth:ServiceCode"] = "portal-web";
        }

        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new ServiceCollection()
            .AddControllers()
            .Services
            .AddPortalDataSource(configuration);
    }
}
