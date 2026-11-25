using System.IO.Compression;
using System.Net;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Api.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class ReportArchiveLayoutTests
{
    [Fact]
    public void EachAnalysisTypeHasItsOwnFolderWithoutAccents()
    {
        EquipmentReportArchive archive = Archive(
            new ReportArchiveCandidate("a", ReportAnalysisType.Chromatography, new DateOnly(2026, 3, 18), "LACTEC-01188/2026"),
            new ReportArchiveCandidate("b", ReportAnalysisType.PhysicalChemical, new DateOnly(2025, 12, 10), "LACTEC-04502/2025"),
            new ReportArchiveCandidate("c", ReportAnalysisType.Other, new DateOnly(2025, 9, 15), "LACTEC-03781/2025"));

        Assert.Equal(
            [
                "Cromatografia/2026-03-18_LACTEC-01188-2026.pdf",
                "Fisico-Quimica/2025-12-10_LACTEC-04502-2025.pdf",
                "Outros-Ensaios/2025-09-15_LACTEC-03781-2025.pdf"
            ],
            archive.Entries.Select(entry => entry.Name));
    }

    [Fact]
    public void RepeatedNamesGetANumberedSuffixAndRepeatedSourcesEnterOnce()
    {
        DateOnly day = new(2026, 6, 23);

        EquipmentReportArchive archive = Archive(
            new ReportArchiveCandidate("a", ReportAnalysisType.Chromatography, day, "REL 1"),
            new ReportArchiveCandidate("b", ReportAnalysisType.Chromatography, day, "rel-1"),
            new ReportArchiveCandidate("c", ReportAnalysisType.Chromatography, day, "REL/1"),
            new ReportArchiveCandidate("a", ReportAnalysisType.Chromatography, day, "REL 1"));

        Assert.Equal(
            [
                "Cromatografia/2026-06-23_REL-1.pdf",
                "Cromatografia/2026-06-23_rel-1-2.pdf",
                "Cromatografia/2026-06-23_REL-1-3.pdf"
            ],
            archive.Entries.Select(entry => entry.Name));
    }

    [Fact]
    public void ANumberedSuffixNeverTakesTheNameOfAnotherRealNumber()
    {
        DateOnly day = new(2026, 6, 23);

        EquipmentReportArchive archive = Archive(
            new ReportArchiveCandidate("a", ReportAnalysisType.Chromatography, day, "REL 1"),
            new ReportArchiveCandidate("b", ReportAnalysisType.Chromatography, day, "REL/1"),
            new ReportArchiveCandidate("c", ReportAnalysisType.Chromatography, day, "REL-1-2"),
            new ReportArchiveCandidate("d", ReportAnalysisType.Chromatography, day, "REL-1-3"),
            new ReportArchiveCandidate("e", ReportAnalysisType.Chromatography, day, "REL.1"));

        string[] names = [.. archive.Entries.Select(entry => entry.Name)];

        Assert.Equal(5, names.Distinct(StringComparer.OrdinalIgnoreCase).Count());
        Assert.Equal(
            [
                "Cromatografia/2026-06-23_REL-1.pdf",
                "Cromatografia/2026-06-23_REL-1-2.pdf",
                "Cromatografia/2026-06-23_REL-1-2-2.pdf",
                "Cromatografia/2026-06-23_REL-1-3.pdf",
                "Cromatografia/2026-06-23_REL-1-4.pdf"
            ],
            names);
    }

    [Theory]
    [InlineData("Óleo Ação/Ç..ü", "Oleo-Acao-C-u")]
    [InlineData("../../etc/passwd", "etc-passwd")]
    [InlineData("   ", "laudo")]
    [InlineData("///", "laudo")]
    public void ANumberBecomesASafeSegment(string number, string expected)
    {
        Assert.Equal(expected, EquipmentReportArchive.SafeSegment(number, "laudo"));
    }

    [Fact]
    public void TheArchiveIsNamedAfterTheSanitizedEquipment()
    {
        EquipmentReportArchive archive = new("111.594/1", [], (_, _) => Task.FromResult<Stream?>(null));

        Assert.Equal("laudos-111-594-1.zip", archive.FileName);
    }

    [Fact]
    public async Task ADocumentThatVanishedIsSkippedAndTheOthersAreReadInOrder()
    {
        List<string> asked = [];
        EquipmentReportArchive archive = new(
            "571289",
            [
                new ReportArchiveCandidate("a", ReportAnalysisType.Chromatography, new DateOnly(2026, 1, 2), "A"),
                new ReportArchiveCandidate("b", ReportAnalysisType.Chromatography, new DateOnly(2026, 1, 1), "B")
            ],
            (key, _) =>
            {
                asked.Add(key);
                return Task.FromResult<Stream?>(key == "a" ? null : new MemoryStream([1]));
            });

        List<string> names = [];

        await foreach (ArchivedReportFile file in archive.ReadFilesAsync(CancellationToken.None))
            names.Add(file.Name);

        Assert.Equal(["a", "b"], asked);
        Assert.Equal(["Cromatografia/2026-01-01_B.pdf"], names);
    }

    [Fact]
    public async Task ACancelledReadStopsBeforeTheNextDocument()
    {
        using CancellationTokenSource cancellation = new();
        int reads = 0;
        EquipmentReportArchive archive = new(
            "571289",
            [
                new ReportArchiveCandidate("a", ReportAnalysisType.Chromatography, new DateOnly(2026, 1, 2), "A"),
                new ReportArchiveCandidate("b", ReportAnalysisType.Chromatography, new DateOnly(2026, 1, 1), "B")
            ],
            (_, _) =>
            {
                reads++;
                cancellation.Cancel();
                return Task.FromResult<Stream?>(new MemoryStream([1]));
            });

        await Assert.ThrowsAnyAsync<OperationCanceledException>(async () =>
        {
            await foreach (ArchivedReportFile _ in archive.ReadFilesAsync(cancellation.Token)) { }
        });

        Assert.Equal(1, reads);
    }

    [Fact]
    public async Task AnEquipmentWithMoreReportsThanTheCeilingIsRefused()
    {
        SampleAnalysis[] analyses = [.. Enumerable.Range(1, EquipmentReportArchive.MaxReports + 1)
            .Select(index => AnalysisBuilder.Valid(
                protocoloLactec: $"{index:D5}/2026-001",
                identification: $"LACTEC-{index:D5}/2026",
                analysisEndDate: new DateTime(2026, 1, 1).AddDays(index)))];

        AllSigned documents = new();
        EquipmentReportArchiving archiving = new(new InMemoryLabTestRepository(analyses), documents);

        ReportArchiveTooLargeException refused = await Assert.ThrowsAsync<ReportArchiveTooLargeException>(() =>
            archiving.OpenArchiveAsync(TestCompanies.ScopeOf(AnalysisBuilder.SampleCompany), "571289", CancellationToken.None));

        Assert.Equal(EquipmentReportArchive.MaxReports + 1, refused.ReportCount);
        Assert.Equal(0, documents.DocumentReads);
    }

    private static EquipmentReportArchive Archive(params ReportArchiveCandidate[] candidates)
    {
        return new EquipmentReportArchive("571289", candidates, (_, _) => Task.FromResult<Stream?>(null));
    }

    private sealed class AllSigned : IReportIssueRepository
    {
        public int DocumentReads { get; private set; }

        public Task<SignedReportDocument?> GetSignedDocumentOfAsync(
            CompanyScope scope, string reportIdentification, CancellationToken cancellationToken)
        {
            DocumentReads++;
            return Task.FromResult<SignedReportDocument?>(new SignedReportDocument("x.pdf", [1]));
        }

        public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
            CompanyScope scope, IReadOnlyCollection<string> reportIdentifications, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlySet<string>>(new HashSet<string>(reportIdentifications));

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

public sealed class ReportArchiveContractTests : IClassFixture<RunningPortal>
{
    private static readonly byte[] Pdf = [0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37];

    private readonly RunningPortal _portal;

    public ReportArchiveContractTests(RunningPortal portal)
    {
        _portal = portal;
    }

    [Fact]
    public async Task TheArchiveStreamsOnePdfPerSignedReportInFoldersByAnalysisType()
    {
        Documents documents = new() { Content = Pdf };
        using WebApplicationFactory<Program> portal = With(documents);
        using HttpClient client = portal.CreateClient();
        string equipment = OpaqueIdentifier.Encode("571289");

        HttpResponseMessage history = await client.GetAsync($"/api/equipments/{equipment}/reports");
        int reports = JsonNode.Parse(await history.Content.ReadAsStringAsync())!.AsArray().Count;

        HttpResponseMessage response = await client.GetAsync($"/api/equipments/{equipment}/reports/archive");

        response.EnsureSuccessStatusCode();
        Assert.Equal("application/zip", response.Content.Headers.ContentType?.MediaType);
        Assert.Equal("attachment", response.Content.Headers.ContentDisposition?.DispositionType);
        Assert.Equal("attachment; filename=\"laudos-571289.zip\"", string.Join(";", response.Content.Headers.GetValues("Content-Disposition")));

        using ZipArchive zip = new(new MemoryStream(await response.Content.ReadAsByteArrayAsync()));

        Assert.InRange(zip.Entries.Count, 1, reports);
        Assert.Equal(documents.Asked.Count, zip.Entries.Count);
        Assert.All(zip.Entries, entry =>
        {
            Assert.Matches(@"^(Cromatografia|Fisico-Quimica|Outros-Ensaios)/\d{4}-\d{2}-\d{2}_[A-Za-z0-9-]+\.pdf$", entry.FullName);
            using MemoryStream read = new();
            entry.Open().CopyTo(read);
            Assert.Equal(Pdf, read.ToArray());
        });
    }

    [Fact]
    public async Task TheSignedPdfsOfTheSampleAreTheOnesTheSingleDownloadServes()
    {
        HttpResponseMessage response = await _portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports/archive");

        response.EnsureSuccessStatusCode();

        using ZipArchive zip = new(new MemoryStream(await response.Content.ReadAsByteArrayAsync()));

        Assert.NotEmpty(zip.Entries);
        Assert.All(zip.Entries, entry =>
        {
            using StreamReader reader = new(entry.Open(), System.Text.Encoding.Latin1);
            Assert.StartsWith("%PDF-", reader.ReadToEnd(), StringComparison.Ordinal);
        });
    }

    [Fact]
    public async Task AnEquipmentWithoutAnySignedPdfAnswersNoContent()
    {
        Documents documents = new() { Content = Pdf, NothingSigned = true };
        using WebApplicationFactory<Program> portal = With(documents);

        HttpResponseMessage response = await portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports/archive");

        Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
        Assert.Empty(documents.Asked);
    }

    [Fact]
    public async Task AnotherTenantsEquipmentIsNotFoundAndNoDocumentIsRead()
    {
        Documents documents = new() { Content = Pdf };
        using WebApplicationFactory<Program> portal = With(documents);

        HttpResponseMessage foreign = await portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("5233")}/reports/archive");
        HttpResponseMessage unknown = await portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("nao-existe")}/reports/archive");

        Assert.Equal(HttpStatusCode.NotFound, foreign.StatusCode);
        Assert.Equal(HttpStatusCode.NotFound, unknown.StatusCode);
        Assert.Contains("equipment-without-analysis", await foreign.Content.ReadAsStringAsync(), StringComparison.Ordinal);
        Assert.Empty(documents.Asked);
    }

    [Fact]
    public async Task TheFallOfTheDocumentSourceBeforeTheFirstFileIsABadGateway()
    {
        Documents documents = new() { Failure = new ReportDocumentSourceUnavailableException("queda interna") };
        using WebApplicationFactory<Program> portal = With(documents);

        HttpResponseMessage response = await portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports/archive");

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.DoesNotContain("queda interna", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task AFailureAfterTheFirstFileAbortsTheResponseInsteadOfDeliveringATruncatedZip()
    {
        Documents documents = new() { Content = Pdf, FailFromSecondRead = true };
        using WebApplicationFactory<Program> portal = With(documents);

        await Assert.ThrowsAnyAsync<Exception>(async () =>
        {
            HttpResponseMessage response = await portal.CreateClient()
                .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports/archive");
            using ZipArchive zip = new(new MemoryStream(await response.Content.ReadAsByteArrayAsync()));
        });

        Assert.Equal(2, documents.Asked.Count);
    }

    [Fact]
    public async Task AnArchiveAboveTheCeilingIsAnUnprocessableProblem()
    {
        using WebApplicationFactory<Program> portal = _portal.WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services =>
                services.AddScoped<IForArchivingEquipmentReports, TooLarge>()));

        HttpResponseMessage response = await portal.CreateClient()
            .GetAsync($"/api/equipments/{OpaqueIdentifier.Encode("571289")}/reports/archive");

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        Assert.Equal("application/problem+json", response.Content.Headers.ContentType?.MediaType);
        Assert.Contains("report-archive-too-large", await response.Content.ReadAsStringAsync(), StringComparison.Ordinal);
    }

    [Fact]
    public async Task AnUnreadableEquipmentIdentifierIsABadRequest()
    {
        HttpResponseMessage response = await _portal.CreateClient().GetAsync("/api/equipments/%25%25/reports/archive");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
    }

    private WebApplicationFactory<Program> With(Documents documents)
    {
        return _portal.WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
            services.AddScoped<IReportIssueRepository>(_ => documents)));
    }

    private sealed class TooLarge : IForArchivingEquipmentReports
    {
        public Task<EquipmentReportArchive?> OpenArchiveAsync(
            CompanyScope scope, string equipmentKey, CancellationToken cancellationToken) =>
            throw new ReportArchiveTooLargeException(EquipmentReportArchive.MaxReports + 1, EquipmentReportArchive.MaxReports);
    }

    private sealed class Documents : IReportIssueRepository
    {
        public byte[]? Content { get; init; }

        public bool NothingSigned { get; init; }

        public bool FailFromSecondRead { get; init; }

        public ReportDocumentSourceUnavailableException? Failure { get; init; }

        public List<string> Asked { get; } = [];

        public Task<SignedReportDocument?> GetSignedDocumentOfAsync(
            CompanyScope scope, string reportIdentification, CancellationToken cancellationToken)
        {
            Asked.Add(reportIdentification);

            if (Failure is not null) throw Failure;

            if (FailFromSecondRead && Asked.Count > 1)
                throw new ReportDocumentSourceUnavailableException("queda no meio");

            return Task.FromResult(Content is null ? null : new SignedReportDocument("x.pdf", Content));
        }

        public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
            CompanyScope scope, IReadOnlyCollection<string> reportIdentifications, CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlySet<string>>(NothingSigned
                ? new HashSet<string>()
                : new HashSet<string>(reportIdentifications));

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
