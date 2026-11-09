using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using Portal.Api.LabResults;
using Portal.Application.LabResults;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.LabResults;

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
    public void SqlServerSourceRegistersTheAutoLabAdapter()
    {
        IServiceCollection services = new ServiceCollection()
            .AddLabTestRepository(AutoLabSettings.Configuration(AutoLabSettings.Complete("LabResults:Source")));

        Assert.IsType<Portal.Adapters.LabResults.AutoLabLabTestRepository>(AutoLabSettings.ResolveInScope<ILabTestRepository>(services));
    }

    [Fact]
    public void SqlServerSourceWithoutTheAutoLabSettingsFailsValidation()
    {
        IServiceCollection services = new ServiceCollection()
            .AddLabTestRepository(AutoLabSettings.Configuration(AutoLabSettings.WithoutDatabase("LabResults:Source")));

        Assert.Throws<OptionsValidationException>(() => AutoLabSettings.ResolveInScope<ILabTestRepository>(services));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("todos")]
    public void MissingOrUnknownScopeBringsStartupDown(string? scope)
    {
        Dictionary<string, string?> values = AutoLabSettings.Complete("LabResults:Source");
        values["LabResults:Scope"] = scope;

        InvalidOperationException failure = Assert.Throws<InvalidOperationException>(() => new ServiceCollection()
            .AddLabTestRepository(AutoLabSettings.Configuration(values)));

        Assert.Contains("LabResults:Scope", failure.Message, StringComparison.Ordinal);
    }

    private static IServiceCollection Register(string? source)
    {
        Dictionary<string, string?> values = new() { ["LabResults:Scope"] = "all" };

        if (source is not null) values["LabResults:Source"] = source;

        IConfiguration configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(values)
            .Build();

        return new ServiceCollection().AddLabTestRepository(configuration);
    }
}
