using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using Portal.Adapters.BackendAuth;
using Portal.Adapters.BackendLab;
using Portal.Adapters.Quotes;
using Portal.Api.Configuration;
using Portal.Api.Reports;
using Portal.Adapters.Reports;
using Portal.Adapters.Salesforce;
using Portal.Application.Authentication;
using Portal.Application.Quotes;
using Portal.Application.Reports;

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
    public void BackendLabSelecionaAConsultaDocumentalSpring()
    {
        IConfiguration configuration = Configuration("backend-lab", useDemoData: true);
        using ServiceProvider provider = Register("backend-lab", useDemoData: true)
            .AddReportDocumentRepository(configuration)
            .BuildServiceProvider();
        using IServiceScope scope = provider.CreateScope();

        Assert.IsType<SpringReportDocumentRepository>(
            scope.ServiceProvider.GetRequiredService<IReportDocumentRepository>());
    }

    [Theory]
    [InlineData(true)]
    [InlineData(false)]
    public void LactecPreservaOsRelatoriosDeExemploLegadosIndependentementeDaDemoComercial(bool useDemoData)
    {
        IConfiguration configuration = Configuration("lactec", useDemoData: useDemoData);
        IServiceCollection services = Register("lactec", useDemoData: useDemoData)
            .AddReportDocumentRepository(configuration);
        ServiceDescriptor registration = services.Last(item =>
            item.ServiceType == typeof(IReportDocumentRepository));

        Assert.Equal(typeof(LactecDemoReportDocumentRepository), registration.ImplementationType);
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
    public void LactecSemDemoRegistraSalesforceComAsOpcoesVinculadas()
    {
        using ServiceProvider provider = Register("lactec").BuildServiceProvider();
        using IServiceScope scope = provider.CreateScope();

        Assert.IsType<SalesforceQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
        Assert.IsType<SalesforceTokenProvider>(provider.GetRequiredService<ISalesforceTokenProvider>());
        Assert.IsType<SalesforceTokenClient>(provider.GetRequiredService<ISalesforceTokenClient>());
        SalesforceOptions options = provider.GetRequiredService<SalesforceOptions>();
        Assert.Same(provider.GetRequiredService<IOptions<SalesforceOptions>>().Value, options);
        Assert.Equal("https://salesforce.test/", options.Url);
        Assert.Equal("client-id-test", options.ClientId);
    }

    [Theory]
    [InlineData("backend-lab", false)]
    [InlineData("backend-lab", true)]
    [InlineData("lactec", true)]
    public void SalesforceNaoERegistradoForaDeLactecSemDemo(string source, bool useDemoData)
    {
        IServiceCollection services = Register(source, useDemoData: useDemoData, salesforce: false);

        Assert.DoesNotContain(services, item => item.ServiceType == typeof(SalesforceOptions)
            || item.ServiceType == typeof(ISalesforceTokenProvider)
            || item.ServiceType == typeof(ISalesforceTokenClient));
        using ServiceProvider provider = services.BuildServiceProvider();
        using IServiceScope scope = provider.CreateScope();
        Assert.IsNotType<SalesforceQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
    }

    [Theory]
    [InlineData("Salesforce:Url", null, "Salesforce:Url")]
    [InlineData("Salesforce:Url", "salesforce.test", "Salesforce:Url")]
    [InlineData("Salesforce:Url", "ftp://salesforce.test/", "Salesforce:Url")]
    [InlineData("Salesforce:GrantType", "", "Salesforce:GrantType")]
    [InlineData("Salesforce:ClientId", " ", "Salesforce:ClientId")]
    [InlineData("Salesforce:ClientSecret", null, "Salesforce:ClientSecret")]
    public void ConfiguracaoSalesforceInvalidaImpedeUsarOAdapter(string key, string? value, string reported)
    {
        IConfiguration configuration = Configuration("lactec", overrides: new() { [key] = value });
        using ServiceProvider provider = new ServiceCollection()
            .AddControllers()
            .Services
            .AddPortalDataSource(configuration)
            .BuildServiceProvider();

        OptionsValidationException failure = Assert.Throws<OptionsValidationException>(
            () => provider.GetRequiredService<SalesforceOptions>());

        Assert.Contains(reported, failure.Message, StringComparison.Ordinal);
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
        string? source, string? baseUrl = "https://backend-lab.test/", bool useDemoData = false, bool salesforce = true)
    {
        IConfiguration configuration = Configuration(source, baseUrl, useDemoData, salesforce);
        return new ServiceCollection()
            .AddControllers()
            .Services
            .AddPortalDataSource(configuration);
    }

    private static IConfiguration Configuration(
        string? source, string? baseUrl = "https://backend-lab.test/", bool useDemoData = false,
        bool salesforce = true, Dictionary<string, string?>? overrides = null)
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

        if (salesforce)
        {
            values["Salesforce:Url"] = "https://salesforce.test/";
            values["Salesforce:GrantType"] = "client_credentials";
            values["Salesforce:ClientId"] = "client-id-test";
            values["Salesforce:ClientSecret"] = "client-secret-test";
        }

        foreach ((string key, string? value) in overrides ?? [])
            values[key] = value;

        return new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();
    }
}
