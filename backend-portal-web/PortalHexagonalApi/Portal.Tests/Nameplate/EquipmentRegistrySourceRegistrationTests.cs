using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using Portal.Adapters.Nameplate;
using Portal.Api.LabResults;
using Portal.Api.Nameplate;
using Portal.Application.Nameplate;
using Portal.Tests.AutoLab;

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
    public void TheSqlServerSourceRegistersTheAutoLabAdapter()
    {
        IServiceCollection services = new ServiceCollection()
            .AddEquipmentRegistrySource(AutoLabSettings.Configuration(AutoLabSettings.Complete("EquipmentRegistry:Source")));

        Assert.IsType<AutoLabEquipmentRegistryRepository>(AutoLabSettings.ResolveInScope<IEquipmentNameplateRepository>(services));
    }

    [Fact]
    public void TheSqlServerSourceWithoutTheAutoLabSettingsFailsValidation()
    {
        IServiceCollection services = new ServiceCollection()
            .AddEquipmentRegistrySource(AutoLabSettings.Configuration(AutoLabSettings.WithoutDatabase("EquipmentRegistry:Source")));

        Assert.Throws<OptionsValidationException>(() => AutoLabSettings.ResolveInScope<IEquipmentNameplateRepository>(services));
    }

    [Fact]
    public void AnUnknownSourceBringsStartupDownNamingTheValue()
    {
        InvalidOperationException failure = Assert.Throws<InvalidOperationException>(
            () => Register("sqlserver"));

        Assert.Contains("sqlserver", failure.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void TheRegistryCanBeOffWhileTheLabTestSourceIsOn()
    {
        IConfiguration configuration = AutoLabSettings.Configuration(AutoLabSettings.Complete("LabResults:Source"));

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
