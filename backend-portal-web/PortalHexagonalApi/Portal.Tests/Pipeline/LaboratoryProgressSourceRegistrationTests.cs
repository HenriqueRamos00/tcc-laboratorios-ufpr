using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.Pipeline;
using Portal.Api.Pipeline;
using Portal.Application.Pipeline;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// O configurador. Fonte ausente cai no ator que nao diz nada, e e a unica configuracao
/// desta API com padrao: sem ela a listagem de orcamentos volta a exibir a etapa do lado
/// comercial, que e o comportamento anterior a esta fatia. Fonte escrita errado continua
/// derrubando a inicializacao, porque um padrao que absorvesse o erro de digitacao
/// esconderia a fatia desligada num ambiente que pensa que a ligou.
/// </summary>
public sealed class LaboratoryProgressSourceRegistrationTests
{
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("not-connected")]
    public void WithoutASourceTheLaboratorySaysNothing(string? source)
    {
        ServiceProvider provider = Register(source).BuildServiceProvider();

        Assert.IsType<NullLaboratoryProgressRepository>(
            provider.GetRequiredService<ILaboratoryProgressRepository>());
    }

    [Fact]
    public void TheInMemorySampleSourceRegistersTheWrittenSampleAdapter()
    {
        ServiceProvider provider = Register("in-memory-sample").BuildServiceProvider();

        Assert.IsType<InMemorySituaProcessRepository>(
            provider.GetRequiredService<ILaboratoryProgressRepository>());
    }

    [Fact]
    public void TheSqlServerSourceAnnouncesThatItHasNoAdapter()
    {
        Assert.Throws<NotSupportedException>(() => Register("sql-server"));
    }

    [Fact]
    public void AnUnknownSourceBringsStartupDownNamingTheValue()
    {
        InvalidOperationException failure = Assert.Throws<InvalidOperationException>(
            () => Register("in-memory-sampl"));

        Assert.Contains("in-memory-sampl", failure.Message, StringComparison.Ordinal);
    }

    private static IServiceCollection Register(string? source)
    {
        Dictionary<string, string?> values = [];

        if (source is not null) values["LaboratoryProgress:Source"] = source;

        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new ServiceCollection().AddLaboratoryProgressSource(configuration);
    }
}
