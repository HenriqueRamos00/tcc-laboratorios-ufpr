using Portal.Adapters.AutoLab;
using Portal.Adapters.LabResults;
using Portal.Adapters.ReportDocuments;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

using UglyToad.PdfPig;

using Xunit;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabSampleTests : IDisposable
{
    private static readonly VisibilityScope Energy = VisibilityScope.OfCompany("EMPRESA DE ENERGIA");
    private static readonly VisibilityScope Competitor = VisibilityScope.OfCompany("CONCORRENTE S.A.");

    private readonly AutoLabTestDatabase _database = AutoLabTestDatabase.WithSample();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Fact]
    public void TheSampleCarriesTheElevenProtocols()
    {
        Assert.Equal(
            [
                "00188/2026-004",
                "00712/2026-001",
                "00894/2026-004",
                "01351/2026-004",
                "01417/2026-001",
                "01439/2026-004",
                "01482/2026-001",
                "01563/2026-018",
                "01718/2026-005",
                "02114/2025-001",
                "02630/2025-001"
            ],
            SampleAnalyses.Whole.Select(analysis => analysis.ProtocoloLactec).Order(StringComparer.Ordinal));
    }

    [Fact]
    public void NullInTheSourceArrivesAsTestNotPerformed()
    {
        SampleAnalysis analysis = SampleAnalyses.OfProtocol("01351/2026-004");

        Assert.Equal(6, analysis.Measurements.Count);
        Assert.All(analysis.Measurements, measurement =>
            Assert.IsType<MeasuredValue.TestNotPerformed>(measurement.Value));
        Assert.Equal("111.594/1", analysis.SerialNumber);
    }

    [Fact]
    public async Task TheAllScopeSeesEveryCompany()
    {
        Page<SampleAnalysis> page = await LabTests().GetAnalysesAsync(
            VisibilityScope.All, PageRequest.First, CancellationToken.None);

        Assert.Equal(6, page.TotalCount);
        Assert.Equal(
            ["DISTRIBUIDORA DE ENERGIA", "EMPRESA DE ENERGIA"],
            page.Items.Select(analysis => analysis.Company).Distinct().Order(StringComparer.Ordinal));
    }

    [Fact]
    public async Task TheCompanyScopeOnlySeesThatCompany()
    {
        Page<SampleAnalysis> ofTheOwner = await LabTests().GetAnalysesOfEquipmentAsync(
            Energy, "571289", new PageRequest(1, 1), CancellationToken.None);
        Page<SampleAnalysis> ofAnother = await LabTests().GetAnalysesOfEquipmentAsync(
            Competitor, "571289", new PageRequest(1, 1), CancellationToken.None);
        Page<SampleAnalysis> listing = await LabTests().GetAnalysesAsync(
            Energy, PageRequest.First, CancellationToken.None);

        Assert.Equal(4, ofTheOwner.TotalCount);
        Assert.Empty(ofAnother.Items);
        Assert.Equal(4, listing.TotalCount);
        Assert.All(listing.Items, analysis => Assert.Equal("EMPRESA DE ENERGIA", analysis.Company));
        Assert.Null(await LabTests().GetAnalysisByProtocolAsync(Competitor, "01417/2026-001", CancellationToken.None));
    }

    [Fact]
    public void AProtocolWithOnlyTestsOutsideTheKnownListIsKeptWithoutMeasuredItems()
    {
        VwDadosEnsaiosRecord row = new()
        {
            Identificacao = "LACTEC-09999/2026",
            ProtocoloLactec = "09999/2026-001",
            NumSerie = "571289",
            Name = "Furfuraldeído",
            Resultado = "12",
            EmpresaTrafo = "EMPRESA DE ENERGIA",
            DataEmissao = new DateTime(2026, 6, 24),
            DataFinalAnalise = new DateTime(2026, 6, 23)
        };

        SampleAnalysis analysis = Assert.Single(AutoLabLabTestRepository.BuildAnalyses([row]));

        Assert.Equal("09999/2026-001", analysis.ProtocoloLactec);
        Assert.Empty(analysis.Measurements);
    }

    [Fact]
    public async Task EveryReportOfTheViewMatchesALaudoOfTheTestView()
    {
        Page<LabReport> reports = await Issues().ListReportsAsync(
            VisibilityScope.All, null, PageRequest.First, CancellationToken.None);
        HashSet<string> laudos = [.. SampleAnalyses.Whole.Select(analysis => analysis.Identification)];

        Assert.Equal(11, reports.TotalCount);
        Assert.All(reports.Items, report => Assert.Contains(report.Identification!, laudos));
    }

    [Fact]
    public async Task TheSignedPdfsOfTheSampleAreValidOnePageDocuments()
    {
        Page<LabReport> reports = await Issues().ListReportsAsync(
            VisibilityScope.All, null, PageRequest.First, CancellationToken.None);

        foreach (LabReport report in reports.Items.Where(report => report.HasSignedDocument))
        {
            SignedReportDocument document = Assert.IsType<SignedReportDocument>(await Issues()
                .GetSignedDocumentOfReportAsync(VisibilityScope.All, report.Id, CancellationToken.None));

            using PdfDocument pdf = PdfDocument.Open(document.Content);

            Assert.Equal(1, pdf.NumberOfPages);
            Assert.Contains(report.Identification!, pdf.GetPage(1).Text, StringComparison.Ordinal);
        }
    }

    [Fact]
    public async Task TheReplacedRevisionAndTheDeletedReportAreNotServedForTheLaudo()
    {
        SignedReportDocument? revised = await Issues()
            .GetSignedDocumentOfAsync("LACTEC-02354 R1/2026", CancellationToken.None);
        IReadOnlySet<string> signed = await Issues().GetIdentificationsWithSignedDocumentAsync(
            ["LACTEC-02354 R1/2026", "LACTEC-04502/2025", "LACTEC-02465/2026", "LACTEC-02272/2026"],
            CancellationToken.None);

        Assert.Equal("LACTEC-02354 R1-2026_rev1.pdf", revised?.FileName);
        Assert.Equal(["LACTEC-02272/2026", "LACTEC-02354 R1/2026"], signed.Order(StringComparer.Ordinal));
        Assert.Null(await Issues().GetSignedDocumentOfAsync("LACTEC-04502/2025", CancellationToken.None));
    }

    [Fact]
    public async Task TheCurrentIssueCarriesTheTitleAndTheConclusionInParagraphs()
    {
        ReportIssue? issue = await Issues().GetCurrentIssueOfAsync("LACTEC-02272/2026", CancellationToken.None);
        ReportIssue? withoutText = await Issues().GetCurrentIssueOfAsync("LACTEC-01502/2026", CancellationToken.None);

        Assert.StartsWith("Relatório de ensaio", issue?.Title, StringComparison.Ordinal);
        Assert.Equal(3, issue?.Paragraphs.Count);
        Assert.Null(withoutText?.Title);
        Assert.Empty(withoutText!.Paragraphs);
    }

    [Fact]
    public async Task TheReportListingFiltersByQuoteAndByCompany()
    {
        Page<LabReport> ofQuote = await Issues().ListReportsAsync(
            VisibilityScope.All, "EAQ_2026_45496_V_1", PageRequest.First, CancellationToken.None);
        Page<LabReport> ofCompetitor = await Issues().ListReportsAsync(
            Competitor, null, PageRequest.First, CancellationToken.None);
        Page<LabReport> ofEnergy = await Issues().ListReportsAsync(
            Energy, null, PageRequest.First, CancellationToken.None);

        LabReport only = Assert.Single(ofQuote.Items);
        Assert.Equal("LACTEC-02962/2026", only.Identification);
        Assert.Empty(ofCompetitor.Items);
        Assert.Equal(9, ofEnergy.TotalCount);
        Assert.All(ofEnergy.Items, report => Assert.Equal("EMPRESA DE ENERGIA", report.Client));
    }

    [Fact]
    public async Task TheReportOfAnotherCompanyIsNotDownloaded()
    {
        Page<LabReport> reports = await Issues().ListReportsAsync(
            VisibilityScope.All, "EAQ_2026_45496_V_1", PageRequest.First, CancellationToken.None);

        Assert.Null(await Issues().GetSignedDocumentOfReportAsync(
            Energy, reports.Items[0].Id, CancellationToken.None));
    }

    private AutoLabLabTestRepository LabTests()
    {
        return new AutoLabLabTestRepository(_database.NewContext(), new AutoLabQueryLimits());
    }

    private AutoLabReportIssueRepository Issues()
    {
        return new AutoLabReportIssueRepository(_database.NewContext(), new AutoLabQueryLimits());
    }
}
