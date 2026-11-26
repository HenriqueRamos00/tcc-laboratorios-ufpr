using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Reports;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Domain.Reports;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class ReportArchiveCeilingBoundaryTests
{
    [Theory]
    [InlineData(EquipmentReportArchive.MaxReports - 1)]
    [InlineData(EquipmentReportArchive.MaxReports)]
    public async Task AnEquipmentUpToTheCeilingGetsEveryReportInTheArchive(int reportCount)
    {
        SampleAnalysis[] analyses = [.. Enumerable.Range(1, reportCount)
            .Select(index => AnalysisBuilder.Valid(
                protocoloLactec: $"{index:D5}/2026-001",
                identification: $"LACTEC-{index:D5}/2026",
                analysisEndDate: new DateTime(2026, 1, 1).AddDays(index)))];

        EquipmentReportArchiving archiving = new(new InMemoryLabTestRepository(analyses), new EverySigned());

        EquipmentReportArchive? archive = await archiving.OpenArchiveAsync(
            TestCompanies.ScopeOf(AnalysisBuilder.SampleCompany), "571289", CancellationToken.None);

        Assert.NotNull(archive);
        Assert.Equal(reportCount, archive!.Entries.Count);
    }

    private sealed class EverySigned : IReportIssueRepository
    {
        public Task<SignedReportDocument?> GetSignedDocumentOfAsync(
            CompanyScope scope, string reportIdentification, CancellationToken cancellationToken) =>
            Task.FromResult<SignedReportDocument?>(new SignedReportDocument("x.pdf", [1]));

        public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
            CompanyScope scope, IReadOnlyCollection<string> reportIdentifications, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlySet<string>>(new HashSet<string>(reportIdentifications, StringComparer.Ordinal));

        public Task<IReadOnlyList<ReportReading>> ReadReportsAsync(
            CompanyScope scope, IReadOnlyList<string> reportIdentifications, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<ReportReading>>([]);

        public Task<Page<LabReport>> ListReportsAsync(
            CompanyScope scope, string? quoteCode, PageRequest page, CancellationToken cancellationToken) =>
            Task.FromResult(Page<LabReport>.Empty(page));

        public Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
            CompanyScope scope, Guid reportId, CancellationToken cancellationToken) =>
            Task.FromResult<SignedReportDocument?>(null);
    }
}

public sealed class ClientReportArchiveTests
{
    private const string Equipment = "eq-87";

    [Fact]
    public async Task TheSecondPageOfTheClientBackendAlsoEntersTheArchive()
    {
        PagedAnalyses source = new([.. Enumerable.Range(1, 150).Select(index => Analysis(index))]);
        ClientLabResultsConsultation consultation = new(source, new NoDocuments());

        EquipmentReportArchive? archive = await consultation.OpenReportArchiveAsync(
            Equipment, CancellationToken.None);

        Assert.NotNull(archive);
        Assert.Equal(150, archive!.Entries.Count);
        Assert.Equal(2, source.PagesAsked.Count);
        Assert.StartsWith("Cromatografia/", archive.Entries[0].Name, StringComparison.Ordinal);
        Assert.Contains("REL-0150", archive.Entries[0].Name, StringComparison.Ordinal);
    }

    [Fact]
    public async Task AnEquipmentAtTheCeilingIsStillArchivedAndTheFirstAboveItIsRefused()
    {
        ClientLabResultsConsultation atTheCeiling = new(
            new PagedAnalyses([.. Enumerable.Range(1, EquipmentReportArchive.MaxReports).Select(index => Analysis(index))]),
            new NoDocuments());
        ClientLabResultsConsultation aboveIt = new(
            new PagedAnalyses([.. Enumerable.Range(1, EquipmentReportArchive.MaxReports + 1).Select(index => Analysis(index))]),
            new NoDocuments());

        EquipmentReportArchive? archive = await atTheCeiling.OpenReportArchiveAsync(
            Equipment, CancellationToken.None);

        Assert.Equal(EquipmentReportArchive.MaxReports, archive?.Entries.Count);

        ReportArchiveTooLargeException refused = await Assert.ThrowsAsync<ReportArchiveTooLargeException>(
            () => aboveIt.OpenReportArchiveAsync(Equipment, CancellationToken.None));

        Assert.Equal(EquipmentReportArchive.MaxReports + 1, refused.ReportCount);
    }

    [Fact]
    public async Task AnAnalysisWithoutAPublishedPdfDoesNotEnterTheArchive()
    {
        PagedAnalyses source = new([Analysis(1), Analysis(2, pdfAvailable: false)]);
        ClientLabResultsConsultation consultation = new(source, new NoDocuments());

        EquipmentReportArchive? archive = await consultation.OpenReportArchiveAsync(
            Equipment, CancellationToken.None);

        ReportArchiveEntry only = Assert.Single(archive!.Entries);

        Assert.Contains("REL-0001", only.Name, StringComparison.Ordinal);
    }

    private static ClientAnalysis Analysis(int index, bool pdfAvailable = true) => new(
        $"analysis-{index}",
        $"report-{index}",
        $"test-{index}",
        null,
        $"REL-{index:D4}",
        new ClientCompany("company-1", "Empresa"),
        new ClientQuote("quote-1", "P-1"),
        new ClientSample("sample-1", "A-1", "Amostra"),
        new ClientAnalysisEquipment(Equipment, "S-87", "TAG-87", "Transformador"),
        "Análise cromatográfica",
        "chromatography",
        new DateOnly(2026, 11, 13),
        new DateTimeOffset(2026, 1, 1, 12, 0, 0, TimeSpan.Zero).AddDays(index),
        new DateTimeOffset(2026, 1, 1, 12, 0, 0, TimeSpan.Zero).AddDays(index),
        "Classificação técnica",
        "Conclusão do snapshot",
        pdfAvailable,
        []);

    private sealed class PagedAnalyses : IClientLabResultsRepository
    {
        private readonly IReadOnlyList<ClientAnalysis> _analyses;

        public PagedAnalyses(IReadOnlyList<ClientAnalysis> analyses)
        {
            _analyses = analyses;
        }

        public List<PageRequest> PagesAsked { get; } = [];

        public Task<Page<ClientEquipment>> ListEquipmentAsync(
            EquipmentSearch? search, PageRequest page, CancellationToken cancellationToken) =>
            Task.FromResult(Page<ClientEquipment>.Empty(page));

        public Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken) =>
            Task.FromResult<ClientEquipment?>(null);

        public Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
            string equipmentId, PageRequest page, CancellationToken cancellationToken)
        {
            PagesAsked.Add(page);

            return Task.FromResult<Page<ClientAnalysis>?>(
                string.Equals(equipmentId, Equipment, StringComparison.Ordinal)
                    ? Page<ClientAnalysis>.Of(_analyses, page)
                    : null);
        }

        public Task<ClientAnalysis?> GetAnalysisAsync(string reportId, CancellationToken cancellationToken) =>
            Task.FromResult<ClientAnalysis?>(null);

        public Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken) =>
            Task.FromResult<ClientReportDetail?>(null);
    }

    private sealed class NoDocuments : IReportDocumentRepository
    {
        public Task<IReadOnlyList<PublishedReport>> GetPublishedAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<PublishedReport>>([]);

        public Task<QuotePublishedReports> GetPublishedOfQuoteAsync(string quoteId, CancellationToken cancellationToken) =>
            Task.FromResult(new QuotePublishedReports("P-1", "Análise", "", []));

        public Task<PublishedReportFile?> GetFileAsync(string reportId, CancellationToken cancellationToken) =>
            Task.FromResult<PublishedReportFile?>(null);
    }
}
