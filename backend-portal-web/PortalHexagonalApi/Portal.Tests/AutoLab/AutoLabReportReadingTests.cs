using Microsoft.Extensions.Caching.Memory;

using Portal.Application.ReportDocuments;
using Portal.Tests.ReportDocuments;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabReportReadingTests : IDisposable
{
    private const string Tenant = TestCompanies.Energy;
    private const string Gas = "LACTEC-00001/2026";
    private const string PhysicalChemical = "LACTEC-00002/2026";

    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Fact]
    public async Task EachReportComesWithItsIssueDateAndWhatItsSignedPdfSays()
    {
        _database.AddReportIssue(
            Gas,
            _database.AddFile("gas.pdf", SyntheticReportPdf.Of("Descarga elétrica", null)),
            published: new DateTime(2026, 9, 24));
        _database.AddReportIssue(
            PhysicalChemical,
            _database.AddFile("fq.pdf", SyntheticReportPdf.Of(null, "NORMAL")),
            published: new DateTime(2026, 7, 28));

        IReadOnlyList<ReportReading> readings = await _database.ReportIssues()
            .ReadReportsAsync(TestCompanies.ScopeOf(Tenant), [Gas, PhysicalChemical], CancellationToken.None);

        Assert.Equal([Gas, PhysicalChemical], readings.Select(reading => reading.Identification));
        Assert.Equal(new DateTime(2026, 9, 24), readings[0].IssueDate);
        Assert.Equal("Descarga elétrica", readings[0].Chromatography?.ResultLabel);
        Assert.Null(readings[0].PhysicalChemical);
        Assert.Equal("NORMAL", readings[1].PhysicalChemical?.ResultLabel);
        Assert.Equal(Conformity.NonConforming, readings[0].Chromatography?.Conformity);
        Assert.Equal(Conformity.Normal, readings[1].PhysicalChemical?.Conformity);
        Assert.Equal(ReportReadOutcome.Extracted, readings[0].Origin?.Outcome);
        Assert.Equal(SyntheticReportPdf.PhysicalChemicalConclusionTitle, readings[1].PhysicalChemical?.Title);
    }

    [Fact]
    public async Task TheExtractionOfASignedFileIsReadOnceAndKeptInMemory()
    {
        _database.AddReportIssue(Gas, _database.AddFile("gas.pdf", SyntheticReportPdf.Of("NORMAL", null)));
        MemoryCache findings = new(new MemoryCacheOptions());

        await _database.ReportIssues(findings: findings)
            .ReadReportsAsync(TestCompanies.ScopeOf(Tenant), [Gas], CancellationToken.None);
        IReadOnlyList<ReportReading> again = await _database.ReportIssues(findings: findings)
            .ReadReportsAsync(TestCompanies.ScopeOf(Tenant), [Gas], CancellationToken.None);

        Assert.Equal("NORMAL", Assert.Single(again).Chromatography?.ResultLabel);
        Assert.Single(_database.Commands, command => command.Contains("\"Content\"", StringComparison.Ordinal));
    }

    [Fact]
    public async Task AnUnreadableOrMissingPdfLeavesTheReportListedWithoutText()
    {
        _database.AddReportIssue(
            Gas, _database.AddFile("gas.pdf", [.. "corrompido"u8]), published: new DateTime(2026, 9, 24));
        _database.AddReportIssue(PhysicalChemical, signedPdf: null, published: new DateTime(2026, 7, 28));

        IReadOnlyList<ReportReading> readings = await _database.ReportIssues()
            .ReadReportsAsync(TestCompanies.ScopeOf(Tenant), [Gas, PhysicalChemical], CancellationToken.None);

        Assert.Equal([Gas, PhysicalChemical], readings.Select(reading => reading.Identification));
        Assert.All(readings, reading => Assert.Null(reading.Chromatography));
        Assert.All(readings, reading => Assert.Null(reading.PhysicalChemical));
        Assert.Equal(ReportReadOutcome.Unreadable, readings[0].Origin?.Outcome);
        Assert.Equal(TestCompanies.EnergyCnpj, readings[0].Origin?.Cnpj);
        Assert.Null(readings[1].Origin);
    }

    [Fact]
    public async Task TheReportOfAnotherCompanyIsNotRead()
    {
        _database.AddReportIssue(
            Gas, _database.AddFile("gas.pdf", SyntheticReportPdf.Of("NORMAL", null)), company: TestCompanies.Distributor);

        IReadOnlyList<ReportReading> readings = await _database.ReportIssues()
            .ReadReportsAsync(TestCompanies.ScopeOf(Tenant), [Gas], CancellationToken.None);

        Assert.Empty(readings);
        Assert.DoesNotContain(_database.Commands, command => command.Contains("\"Content\"", StringComparison.Ordinal));
    }

    [Fact]
    public async Task ACancelledReadingStops()
    {
        _database.AddReportIssue(Gas, _database.AddFile("gas.pdf", SyntheticReportPdf.Of("NORMAL", null)));

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => _database.ReportIssues()
            .ReadReportsAsync(TestCompanies.ScopeOf(Tenant), [Gas], new CancellationToken(canceled: true)));
    }
}
