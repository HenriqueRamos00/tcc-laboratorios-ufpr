using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Options;

using Portal.Adapters.AutoLab;
using Portal.Adapters.ReportDocuments;
using Portal.Api.LabResults;
using Portal.Api.Nameplate;
using Portal.Api.Pipeline;
using Portal.Api.ReportDocuments;
using Portal.Application.ReportDocuments;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class ReportDocumentSourceRegistrationTests
{
    private const string SourceKey = "ReportDocuments:Source";

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("not-connected")]
    public void WithoutASourceThereIsNoSignedDocument(string? source)
    {
        Dictionary<string, string?> values = [];

        if (source is not null) values[SourceKey] = source;

        IServiceCollection services = new ServiceCollection()
            .AddReportDocumentSource(AutoLabSettings.Configuration(values));

        Assert.IsType<NullReportIssueRepository>(
            AutoLabSettings.ResolveInScope<IReportIssueRepository>(services));
        Assert.DoesNotContain(services, descriptor => descriptor.ServiceType == typeof(AutoLabDbContext));
    }

    [Fact]
    public void TheSqlServerSourceRegistersTheAutoLabAdapter()
    {
        IServiceCollection services = new ServiceCollection()
            .AddReportDocumentSource(AutoLabSettings.Configuration(AutoLabSettings.Complete(SourceKey)));

        Assert.IsType<AutoLabReportIssueRepository>(
            AutoLabSettings.ResolveInScope<IReportIssueRepository>(services));
    }

    [Fact]
    public void TheSqlServerSourceWithoutTheAutoLabSettingsFailsValidation()
    {
        IServiceCollection services = new ServiceCollection()
            .AddReportDocumentSource(AutoLabSettings.Configuration(AutoLabSettings.WithoutDatabase(SourceKey)));

        Assert.Throws<OptionsValidationException>(
            () => AutoLabSettings.ResolveInScope<IReportIssueRepository>(services));
    }

    [Fact]
    public void AnUnknownSourceBringsStartupDownNamingTheValue()
    {
        InvalidOperationException failure = Assert.Throws<InvalidOperationException>(() => new ServiceCollection()
            .AddReportDocumentSource(AutoLabSettings.Configuration(new() { [SourceKey] = "sqlserver" })));

        Assert.Contains("sqlserver", failure.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void FourSqlServerSourcesShareOnePooledContext()
    {
        Dictionary<string, string?> values = AutoLabSettings.Complete(SourceKey);
        values["LabResults:Source"] = "sql-server";
        values["EquipmentRegistry:Source"] = "sql-server";
        values["LaboratoryProgress:Source"] = "sql-server";

        IConfiguration configuration = AutoLabSettings.Configuration(values);

        IServiceCollection services = new ServiceCollection()
            .AddLabTestRepository(configuration)
            .AddEquipmentRegistrySource(configuration)
            .AddLaboratoryProgressSource(configuration)
            .AddReportDocumentSource(configuration);

        Assert.Single(services, descriptor => descriptor.ServiceType == typeof(AutoLabQueryLimits));
        Assert.Single(services, descriptor => descriptor.ServiceType == typeof(AutoLabDbContext));
    }
}
