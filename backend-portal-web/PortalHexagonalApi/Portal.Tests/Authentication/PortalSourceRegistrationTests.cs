using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using Portal.Adapters.Authentication;
using Portal.Adapters.BackendLab;
using Portal.Api.Configuration;
using Portal.Application.Authentication;

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
    public void LactecRegistraAutenticacaoIndisponivelSemEnderecoDoSpring()
    {
        using ServiceProvider provider = Register("lactec", null).BuildServiceProvider();

        Assert.IsType<UnavailableClientAuthenticator>(provider.GetRequiredService<IClientAuthenticator>());
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
        string? source, string? baseUrl = "https://backend-lab.test/")
    {
        Dictionary<string, string?> values = [];
        if (source is not null) values["Portal:DataSource"] = source;
        if (baseUrl is not null) values["BackendLab:BaseUrl"] = baseUrl;

        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new ServiceCollection().AddPortalDataSource(configuration);
    }
}
