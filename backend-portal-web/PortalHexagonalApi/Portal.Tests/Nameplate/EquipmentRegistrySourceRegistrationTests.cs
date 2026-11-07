using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.Nameplate;
using Portal.Api.LabResults;
using Portal.Api.Nameplate;
using Portal.Application.Nameplate;

using Xunit;

namespace Portal.Tests.Nameplate;

/// <summary>
/// O configurador. Fonte ausente cai no ator que nao diz nada, porque sem cadastro a
/// listagem volta ao comportamento anterior a esta fatia e o pior que isso produz e nenhuma
/// placa. Fonte escrita errado continua derrubando a inicializacao, porque um padrao que
/// absorvesse o erro de digitacao esconderia a fatia desligada num ambiente que pensa que a
/// ligou.
/// </summary>
public sealed class EquipmentRegistrySourceRegistrationTests
{
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("not-connected")]
    public void WithoutASourceTheRegistrySaysNothing(string? source)
    {
        ServiceProvider provider = Register(source).BuildServiceProvider();

        Assert.IsType<NullEquipmentNameplateRepository>(
            provider.GetRequiredService<IEquipmentNameplateRepository>());
    }

    [Fact]
    public void TheInMemorySampleSourceRegistersTheWrittenSampleAdapter()
    {
        ServiceProvider provider = Register("in-memory-sample").BuildServiceProvider();

        Assert.IsType<InMemoryEquipmentRegistryRepository>(
            provider.GetRequiredService<IEquipmentNameplateRepository>());
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

    /// <summary>
    /// A fonte do cadastro e ortogonal a fonte dos ensaios, e este caso prova que o portal
    /// sobe com uma ligada e a outra desligada. E como ele roda em producao hoje, e e o que
    /// torna esta fatia entregavel antes de existir conexao com o SQL Server.
    /// </summary>
    [Fact]
    public void TheRegistryCanBeOffWhileTheLabTestSourceIsOn()
    {
        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["LabResults:Source"] = "embedded-sample"
            })
            .Build();

        ServiceProvider provider = new ServiceCollection()
            .AddLabTestRepository(configuration)
            .AddEquipmentRegistrySource(configuration)
            .BuildServiceProvider();

        Assert.IsType<NullEquipmentNameplateRepository>(
            provider.GetRequiredService<IEquipmentNameplateRepository>());
    }

    private static IServiceCollection Register(string? source)
    {
        Dictionary<string, string?> values = [];

        if (source is not null) values["EquipmentRegistry:Source"] = source;

        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new ServiceCollection().AddEquipmentRegistrySource(configuration);
    }
}
