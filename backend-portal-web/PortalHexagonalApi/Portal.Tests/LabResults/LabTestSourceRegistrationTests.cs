using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

using Portal.Api.LabResults;
using Portal.Application.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

/// <summary>
/// A fonte nao tem valor padrao de proposito: um padrao silencioso faria o ambiente errado
/// servir a amostra embutida como se fosse o AutoLAB. Erro de configuracao precisa derrubar
/// a inicializacao, que e o unico momento em que ele e barato de perceber.
/// </summary>
public sealed class LabTestSourceRegistrationTests
{
    [Fact]
    public void MissingSourceBringsStartupDown()
    {
        Assert.Throws<InvalidOperationException>(() => Register(null));
    }

    [Fact]
    public void EmptySourceBringsStartupDown()
    {
        Assert.Throws<InvalidOperationException>(() => Register(string.Empty));
    }

    [Fact]
    public void UnknownSourceBringsStartupDown()
    {
        InvalidOperationException failure = Assert.Throws<InvalidOperationException>(() => Register("postgres"));

        Assert.Contains("postgres", failure.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void SqlServerSourceAnnouncesThatItHasNoAdapter()
    {
        Assert.Throws<NotSupportedException>(() => Register("sql-server"));
    }

    [Fact]
    public void EmbeddedSampleSourceRegistersTheSampleAdapter()
    {
        ServiceProvider provider = Register("embedded-sample").BuildServiceProvider();

        Assert.IsType<Portal.Adapters.LabResults.EmbeddedSampleLabTestRepository>(
            provider.GetRequiredService<ILabTestRepository>());
    }

    private static IServiceCollection Register(string? source)
    {
        Dictionary<string, string?> values = [];

        if (source is not null) values["LabResults:Source"] = source;

        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new ServiceCollection().AddLabTestRepository(configuration);
    }
}
