using Portal.Api.LabResults;
using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Tests.AutoLab;
using Portal.Tests.Nameplate;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class HealthReportsConsultationTests
{
    private const string Serial = "571289";
    private const string Older = "LACTEC-00001/2026";
    private const string Middle = "LACTEC-00002/2026";
    private const string Latest = "LACTEC-00003/2026";

    [Fact]
    public async Task EachTabListsEveryReportWithItsSectionMostRecentFirst()
    {
        ReadingsByIdentification readings = new(
            Reading(Older, new DateTime(2026, 3, 1), gas: Section("NORMAL"), physicalChemical: Section("NORMAL")),
            Reading(Middle, new DateTime(2026, 5, 1), gas: Section("Descarga elétrica")),
            Reading(Latest, new DateTime(2026, 7, 1), physicalChemical: Section("NORMAL")));

        SampleHealthIndicators indicators = await IndicatorsAsync(
            readings,
            null,
            Both("01/2026-001", Older, new DateTime(2026, 2, 20)),
            Gas("02/2026-001", Middle, new DateTime(2026, 4, 20)),
            PhysicalChemical("03/2026-001", Latest, new DateTime(2026, 6, 20)));

        Assert.Equal([Middle, Older], indicators.GasReports.Select(report => report.Identification));
        Assert.Equal([Latest, Older], indicators.PhysicalChemicalReports.Select(report => report.Identification));
        Assert.Equal(new DateTime(2026, 5, 1), indicators.GasReports[0].Date);
        Assert.Equal("Descarga elétrica", indicators.GasReports[0].ResultLabel);
        Assert.Equal("LAUDO", indicators.GasReports[0].Title);
        Assert.Equal(["Parágrafo."], indicators.GasReports[0].Paragraphs);
        Assert.Equal([[Latest, Middle, Older]], readings.Asked);
    }

    [Fact]
    public async Task TheConformityComesFromTheMostRecentReportOfEachSection()
    {
        SampleHealthIndicators indicators = await IndicatorsAsync(
            new ReadingsByIdentification(
                Reading(Older, new DateTime(2026, 3, 1), gas: Section("NORMAL"), physicalChemical: Section("Normal")),
                Reading(Latest, new DateTime(2026, 7, 1), gas: Section("Descarga elétrica"))),
            null,
            Both("01/2026-001", Older, new DateTime(2026, 2, 20)),
            Gas("03/2026-001", Latest, new DateTime(2026, 6, 20)));

        HealthIndicatorsResponse response = PositionalFlattening.Flatten(indicators, "equipamento");

        Assert.Equal("nao-conforme", response.DissolvedGases.Conformity);
        Assert.Equal("Descarga elétrica", response.DissolvedGases.Verdict);
        Assert.Equal("normal", response.PhysicalChemical.Conformity);
        Assert.Equal("Normal", response.PhysicalChemical.Verdict);
    }

    [Fact]
    public async Task TheSelectedReportDecidesTheConformityAndTheListStaysWhole()
    {
        SampleHealthIndicators indicators = await IndicatorsAsync(
            new ReadingsByIdentification(
                Reading(Older, new DateTime(2026, 3, 1), gas: Section("NORMAL")),
                Reading(Latest, new DateTime(2026, 7, 1), gas: Section("Descarga elétrica"))),
            "01/2026-001",
            Gas("01/2026-001", Older, new DateTime(2026, 2, 20)),
            Gas("03/2026-001", Latest, new DateTime(2026, 6, 20)));

        HealthIndicatorsResponse response = PositionalFlattening.Flatten(indicators, "equipamento");

        Assert.Equal("normal", response.DissolvedGases.Conformity);
        Assert.Equal([Latest, Older], response.DissolvedGases.Reports.Select(report => report.Identification));
    }

    [Fact]
    public async Task WithoutALabelOrWithoutThePdfTheConformityIsNullAndTheReportIsStillListed()
    {
        SampleHealthIndicators indicators = await IndicatorsAsync(
            new ReadingsByIdentification(
                Reading(Latest, new DateTime(2026, 7, 1), gas: new ReportSection(null, "LAUDO", []))),
            null,
            Gas("01/2026-001", Older, new DateTime(2026, 2, 20)),
            Gas("03/2026-001", Latest, new DateTime(2026, 6, 20)),
            PhysicalChemical("02/2026-001", Middle, new DateTime(2026, 4, 20)));

        HealthIndicatorsResponse response = PositionalFlattening.Flatten(indicators, "equipamento");

        Assert.Null(response.DissolvedGases.Conformity);
        Assert.Null(response.PhysicalChemical.Conformity);
        Assert.Equal([Latest, Older], response.DissolvedGases.Reports.Select(report => report.Identification));
        Assert.Equal("2026-06-24", response.DissolvedGases.Reports[1].Date);
        Assert.Null(response.DissolvedGases.Reports[1].ResultLabel);
        Assert.Equal(Middle, Assert.Single(response.PhysicalChemical.Reports).Identification);
        Assert.Null(response.PhysicalChemical.Reports[0].Title);
    }

    [Fact]
    public async Task OnlyTheMostRecentReportsAreRead()
    {
        ReadingsByIdentification readings = new();

        SampleAnalysis[] analyses = [.. Enumerable.Range(1, LabResultsConsultation.MaxReportsRead + 6)
            .Select(index => Gas(
                $"{index:00}/2026-001",
                $"LACTEC-{index:00000}/2026",
                new DateTime(2026, 1, 1).AddDays(index)))];

        SampleHealthIndicators indicators = await IndicatorsAsync(readings, null, analyses);

        string[] asked = Assert.Single(readings.Asked);
        Assert.Equal(LabResultsConsultation.MaxReportsRead, asked.Length);
        Assert.Equal($"LACTEC-{LabResultsConsultation.MaxReportsRead + 6:00000}/2026", asked[0]);
        Assert.Equal(LabResultsConsultation.MaxReportsRead, indicators.GasReports.Count);
    }

    private static async Task<SampleHealthIndicators> IndicatorsAsync(
        ReadingsByIdentification readings,
        string? selectedProtocol,
        params SampleAnalysis[] analyses)
    {
        LabResultsConsultation consultation = new(
            new InMemoryLabTestRepository(analyses),
            new InMemoryEquipmentNameplateRepository(),
            readings);

        SampleHealthIndicators? indicators = await consultation.GetHealthIndicatorsAsync(
            new HealthIndicatorsQuery(TestCompanies.ScopeOf(AnalysisBuilder.SampleCompany), Serial, selectedProtocol),
            CancellationToken.None);

        return Assert.IsType<SampleHealthIndicators>(indicators);
    }

    private static ReportSection Section(string label)
    {
        return new ReportSection(label, "LAUDO", ["Parágrafo."]);
    }

    private static ReportReading Reading(
        string identification,
        DateTime issued,
        ReportSection? gas = null,
        ReportSection? physicalChemical = null)
    {
        return new ReportReading(identification, issued, gas, physicalChemical);
    }

    private static SampleAnalysis Gas(string protocol, string identification, DateTime analysisEnd)
    {
        return AnalysisOf(protocol, identification, analysisEnd, AnalysisBuilder.MeasurementOf("Hidrogênio", "60"));
    }

    private static SampleAnalysis PhysicalChemical(string protocol, string identification, DateTime analysisEnd)
    {
        return AnalysisOf(
            protocol, identification, analysisEnd, AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48"));
    }

    private static SampleAnalysis Both(string protocol, string identification, DateTime analysisEnd)
    {
        return AnalysisOf(
            protocol,
            identification,
            analysisEnd,
            AnalysisBuilder.MeasurementOf("Hidrogênio", "60"),
            AnalysisBuilder.MeasurementOf("Rigidez Dielétrica - Calota (kV)", "48"));
    }

    private static SampleAnalysis AnalysisOf(
        string protocol,
        string identification,
        DateTime analysisEnd,
        params Measurement[] measurements)
    {
        return AnalysisBuilder.Valid(
            protocoloLactec: protocol,
            identification: identification,
            serialNumber: Serial,
            analysisEndDate: analysisEnd,
            measurements: measurements);
    }

    private sealed class ReadingsByIdentification(params ReportReading[] readings) : IReportIssueRepository
    {
        public List<string[]> Asked { get; } = [];

        public Task<IReadOnlyList<ReportReading>> ReadReportsAsync(
            CompanyScope scope,
            IReadOnlyList<string> reportIdentifications,
            CancellationToken cancellationToken)
        {
            Asked.Add([.. reportIdentifications]);

            return Task.FromResult<IReadOnlyList<ReportReading>>(
                [.. readings.Where(reading => reportIdentifications.Contains(reading.Identification))]);
        }

        public Task<SignedReportDocument?> GetSignedDocumentOfAsync(
            CompanyScope scope, string reportIdentification, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");

        public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
            CompanyScope scope, IReadOnlyCollection<string> reportIdentifications, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");

        public Task<Page<LabReport>> ListReportsAsync(
            CompanyScope scope, string? quoteCode, PageRequest page, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");

        public Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
            CompanyScope scope, Guid reportId, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");
    }
}
