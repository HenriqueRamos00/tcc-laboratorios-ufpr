using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.Reports;
using Portal.Domain.LabResults;
using Portal.Domain.Reports;

using Xunit;

namespace Portal.Tests.BackendLab;

public sealed class ClientLabResultsConsultationTests
{
    [Fact]
    public async Task SeriesUsaIdPersistidoEDataCivilDeSaoPauloMesmoComProtocoloRepetido()
    {
        ClientAnalysis older = Analysis(
            "report-a", "test-a", "eq-87", "PROTOCOLO-REPETIDO",
            DateTimeOffset.Parse("2026-11-14T01:00:00Z"), [Gas("h2", "h2", 1.250m)]);
        ClientAnalysis newer = Analysis(
            "report-b", "test-b", "eq-87", "PROTOCOLO-REPETIDO",
            DateTimeOffset.Parse("2026-11-15T01:00:00Z"), [Gas("h2", "h2", 2.500m)]);
        ClientLabResultsConsultation consultation = Consultation([newer, older]);

        SampleHealthIndicators? indicators = await consultation.GetHealthIndicatorsAsync(
            "eq-87", null, CancellationToken.None);
        Assert.NotNull(indicators);
        SeriesReading[] readings = indicators!.GasSeries.Readings.ToArray();

        Assert.Equal(["report-a", "report-b"], readings.Select(reading => reading.AnalysisId));
        Assert.Equal(new DateTime(2026, 11, 13, 22, 0, 0), readings[0].ReportDate);
        Assert.Equal(new DateTime(2026, 11, 14, 22, 0, 0), readings[1].ReportDate);
        Assert.Equal(1.250m, Assert.IsType<MeasuredValue.Quantity>(readings[0].ValuesByItemCode["h2"]).NumericValue);
        Assert.Equal(2.500m, Assert.IsType<MeasuredValue.Quantity>(readings[1].ValuesByItemCode["h2"]).NumericValue);
    }

    [Fact]
    public async Task MedicaoDesconhecidaPermaneceNaRespostaComUnidadeMetodoLimiteETipo()
    {
        ClientMeasurement unknown = new(
            "new-code", "Medição nova", "uX", "Método M-9", "limite fornecido",
            new ClientMeasurementResult.Quantity(13.120m));
        ClientLabResultsConsultation consultation = Consultation([
            Analysis("report-a", "test-a", "eq-87", "P-1", DateTimeOffset.Parse("2026-11-14T12:00:00Z"), [unknown])]);

        SampleHealthIndicators? indicators = await consultation.GetHealthIndicatorsAsync(
            "eq-87", null, CancellationToken.None);
        Assert.NotNull(indicators);
        UnreadableResult preserved = Assert.Single(indicators!.UnreadableResults);

        Assert.Equal("new-code", preserved.Code);
        Assert.Equal("13.120", preserved.OriginalText);
        Assert.Equal("uX", preserved.Unit);
        Assert.Equal("Método M-9", preserved.Method);
        Assert.Equal("limite fornecido", preserved.ReferenceLimit);
        Assert.Equal("quantity", preserved.ResultKind);
    }

    [Fact]
    public async Task CadastroSemAnalisesDevolveIndicadoresVaziosSemCriarAnaliseFicticia()
    {
        ClientLabResultsConsultation consultation = Consultation([]);

        SampleHealthIndicators? indicators = await consultation.GetHealthIndicatorsAsync(
            "eq-87", null, CancellationToken.None);

        Assert.NotNull(indicators);
        Assert.Null(indicators!.SelectedAnalysis);
        Assert.Empty(indicators.PhysicalChemicalSeries.Readings);
        Assert.Empty(indicators.GasSeries.Readings);
        Assert.Empty(indicators.UnreadableResults);
        Assert.Equal("TAG-87", indicators.Tag);
    }

    [Fact]
    public async Task LaudoDoBackendLabViraListaDeUmItemSemRotulo()
    {
        ClientLabResultsConsultation consultation = Consultation([
            Analysis("report-a", "test-a", "eq-87", "P-1", DateTimeOffset.Parse("2026-11-14T12:00:00Z"), [Gas("h2", "h2", 1m)])]);

        SampleHealthIndicators? indicators = await consultation.GetHealthIndicatorsAsync(
            "eq-87", null, CancellationToken.None);

        ReportConclusion report = Assert.Single(indicators!.GasReports);
        Assert.Equal("report-a", report.Identification);
        Assert.Equal(new DateTime(2026, 11, 14, 9, 0, 0), report.Date);
        Assert.Null(report.ResultLabel);
        Assert.Equal("Classificação técnica", report.Title);
        Assert.Equal(["Conclusão do snapshot"], report.Paragraphs);
        Assert.Empty(indicators.PhysicalChemicalReports);
        Assert.Null(indicators.GasResult);
    }

    [Fact]
    public async Task AnaliseSelecionadaDeOutroEquipamentoNaoRetornaSerieGeral()
    {
        ClientAnalysis stranger = Analysis(
            "report-other", "test-other", "eq-other", "P-OTHER",
            DateTimeOffset.Parse("2026-11-14T12:00:00Z"), [Gas("h2", "h2", 1m)]);
        ClientLabResultsConsultation consultation = Consultation([], stranger);

        Assert.Null(await consultation.GetHealthIndicatorsAsync("eq-87", "report-other", CancellationToken.None));
    }

    [Fact]
    public async Task PdfNaoSeAssociaAoEquipamentoQuandoSnapshotNaoContemVinculo()
    {
        ClientReportDetail detail = new(
            Analysis("report-a", "test-a", "eq-87", "P-1", DateTimeOffset.Parse("2026-11-14T12:00:00Z"), [], includeEquipment: false),
            "/api/client/reports/report-a/file");
        FakeRepository repository = new([], reportDetail: detail);
        FakeReportDocumentRepository documents = new();
        ClientLabResultsConsultation consultation = new(repository, documents);

        Assert.Null(await consultation.GetReportFileAsync("eq-87", "report-a", CancellationToken.None));
        Assert.False(documents.FileRequested);
    }

    [Fact]
    public async Task PdfVinculadoAoEquipamentoUsaRepositorioDocumentalExistente()
    {
        ClientReportDetail detail = new(
            Analysis("report-a", "test-a", "eq-87", "P-1", DateTimeOffset.Parse("2026-11-14T12:00:00Z"), []),
            "/api/client/reports/report-a/file");
        FakeRepository repository = new([], reportDetail: detail);
        FakeReportDocumentRepository documents = new();
        ClientLabResultsConsultation consultation = new(repository, documents);

        using Stream? stream = await consultation.GetReportFileAsync("eq-87", "report-a", CancellationToken.None);

        Assert.NotNull(stream);
        Assert.True(documents.FileRequested);
    }

    private static ClientLabResultsConsultation Consultation(
        IReadOnlyList<ClientAnalysis> analyses,
        ClientAnalysis? selected = null) => new(new FakeRepository(analyses, selected), new FakeReportDocumentRepository());

    private static ClientAnalysis Analysis(
        string reportId,
        string testId,
        string equipmentId,
        string? protocol,
        DateTimeOffset publishedAt,
        IReadOnlyList<ClientMeasurement> measurements,
        bool includeEquipment = true) => new(
            reportId,
            reportId,
            testId,
            testId,
            reportId,
            new ClientCompany("company-1", "Empresa"),
            new ClientQuote("quote-1", protocol),
            new ClientSample("sample-1", "A-1", "Amostra"),
            includeEquipment ? new ClientAnalysisEquipment(equipmentId, "S-87", "TAG-87", "Transformador") : null,
            "Análise cromatográfica",
            "chromatography",
            new DateOnly(2026, 11, 13),
            publishedAt,
            publishedAt,
            "Classificação técnica",
            "Conclusão do snapshot",
            true,
            measurements);

    private static ClientMeasurement Gas(string code, string indicatorCode, decimal value) => new(
        code, code, "ppm", null, null, new ClientMeasurementResult.Quantity(value), indicatorCode);

    private sealed class FakeRepository(
        IReadOnlyList<ClientAnalysis> analyses,
        ClientAnalysis? selected = null,
        ClientReportDetail? reportDetail = null) : IClientLabResultsRepository
    {
        public Task<Page<ClientEquipment>> ListEquipmentAsync(
            EquipmentSearch? search, PageRequest page, CancellationToken cancellationToken) =>
            Task.FromResult(Page<ClientEquipment>.Of([Equipment], page));

        public Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken) =>
            Task.FromResult<ClientEquipment?>(equipmentId == Equipment.Id ? Equipment : null);

        public Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
            string equipmentId, PageRequest page, CancellationToken cancellationToken) =>
            Task.FromResult<Page<ClientAnalysis>?>(equipmentId == Equipment.Id
                ? Page<ClientAnalysis>.Of(analyses, page)
                : null);

        public Task<ClientAnalysis?> GetAnalysisAsync(string reportId, CancellationToken cancellationToken) =>
            Task.FromResult(selected?.ReportId == reportId ? selected : null);

        public Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken) =>
            Task.FromResult(reportDetail?.Analysis.ReportId == reportId ? reportDetail : null);
    }

    private sealed class FakeReportDocumentRepository : IReportDocumentRepository
    {
        public bool FileRequested { get; private set; }

        public Task<IReadOnlyList<PublishedReport>> GetPublishedAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<PublishedReport>>([]);

        public Task<QuotePublishedReports> GetPublishedOfQuoteAsync(string quoteId, CancellationToken cancellationToken) =>
            Task.FromResult(new QuotePublishedReports("P-1", "Análise", "", []));

        public Task<PublishedReportFile?> GetFileAsync(string reportId, CancellationToken cancellationToken)
        {
            FileRequested = true;
            return Task.FromResult<PublishedReportFile?>(new PublishedReportFile([0x25, 0x50, 0x44, 0x46]));
        }
    }

    private static ClientEquipment Equipment { get; } = new(
        "eq-87", "S-87", "TAG-87", "Transformador", "SE-87", 138m, "Fab", 2001, 50m, "Mineral", 1000m,
        "company-1", "Empresa", 0, null);
}
