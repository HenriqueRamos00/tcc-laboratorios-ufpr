using System.Collections.Concurrent;
using System.Net;
using System.Text;

using Microsoft.AspNetCore.Http;

using Portal.Adapters.BackendLab;
using Portal.Api.Configuration;
using Portal.Application.LabResults;
using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;

using Xunit;

namespace Portal.Tests.BackendLab;

public sealed class BackendLabClientLabResultsRepositoryTests
{
    [Fact]
    public async Task ResumoDeEquipamentoAceitaExatamenteOFormatoSemReferenciasAninhadas()
    {
        using HttpClient client = Client(new ResponseHandler((_, _) => Task.FromResult(JsonResponse(EquipmentPage))));
        BackendLabClientLabResultsRepository repository = new(client);

        Page<ClientEquipment> page = await repository.ListEquipmentAsync(new PageRequest(1, 20), CancellationToken.None);
        ClientEquipment equipment = Assert.Single(page.Items);

        Assert.Equal("eq-opaque-81", equipment.Id);
        Assert.Equal("Transformador", equipment.Type);
        Assert.Equal(138.125m, equipment.PrimaryVoltageKv);
        Assert.Equal("report-81", equipment.LatestPublishedAnalysis?.ReportId);
        Assert.Null(equipment.LatestPublishedAnalysis?.TestCode);
        Assert.Equal(new DateOnly(2026, 11, 13), equipment.LatestPublishedAnalysis?.ScheduledAt);
        Assert.Equal(DateTimeOffset.Parse("2026-11-14T01:00:00Z"), equipment.LatestPublishedAnalysis?.PublishedAt);
        Assert.Equal(20, page.PageSize);
    }

    [Fact]
    public async Task HistoricoPreservaResultadosTipadosPrecisaoUnidadeECodigosDeOrigem()
    {
        using HttpClient client = Client(new ResponseHandler((_, _) => Task.FromResult(JsonResponse(AnalysisPage))));
        BackendLabClientLabResultsRepository repository = new(client);

        Page<ClientAnalysis>? page = await repository.ListAnalysesOfEquipmentAsync(
            "eq/81", new PageRequest(1, 20), CancellationToken.None);
        Assert.NotNull(page);
        ClientAnalysis analysis = Assert.Single(page!.Items);

        Assert.Equal("report-81", analysis.Id);
        Assert.Equal("report-81", analysis.ReportId);
        Assert.Equal("test-11", analysis.TestId);
        Assert.Equal("PROTOCOLO-REPETIVEL", analysis.Quote.Protocol);
        Assert.Equal("chromatography", analysis.AnalysisType);
        Assert.Equal(4, analysis.Measurements.Count);
        Assert.Equal(12.340m, Assert.IsType<ClientMeasurementResult.Quantity>(analysis.Measurements[0].Result).Value);
        Assert.Equal("neutralization-index", analysis.Measurements[0].Code);
        Assert.Equal("neutralizacao", analysis.Measurements[0].IndicatorCode);
        Assert.IsType<ClientMeasurementResult.NotDetected>(analysis.Measurements[1].Result);
        Assert.IsType<ClientMeasurementResult.NotPerformed>(analysis.Measurements[2].Result);
        Assert.Equal("< 0,01", Assert.IsType<ClientMeasurementResult.Text>(analysis.Measurements[3].Result).Value);
    }

    [Fact]
    public async Task HistoricoRecusaMeasurementsNuloMasAceitaListaVaziaExplicita()
    {
        string nullMeasurements = AnalysisPage.Replace(
            "\"measurements\":[", "\"measurements\":null,\"ignoredMeasurements\":[", StringComparison.Ordinal);
        using HttpClient nullClient = Client(new ResponseHandler((_, _) => Task.FromResult(JsonResponse(nullMeasurements))));
        BackendLabClientLabResultsRepository nullRepository = new(nullClient);

        await Assert.ThrowsAsync<ClientLabSourceUnavailableException>(() => nullRepository.ListAnalysesOfEquipmentAsync(
            "eq-81", new PageRequest(1, 20), CancellationToken.None));

        string emptyMeasurements = AnalysisPage.Replace(
            "\"measurements\":[", "\"measurements\":[],\"ignoredMeasurements\":[", StringComparison.Ordinal);
        using HttpClient emptyClient = Client(new ResponseHandler((_, _) => Task.FromResult(JsonResponse(emptyMeasurements))));
        BackendLabClientLabResultsRepository emptyRepository = new(emptyClient);
        Page<ClientAnalysis>? page = await emptyRepository.ListAnalysesOfEquipmentAsync(
            "eq-81", new PageRequest(1, 20), CancellationToken.None);

        Assert.NotNull(page);
        Assert.Empty(Assert.Single(page!.Items).Measurements);
    }

    [Fact]
    public async Task DetalheDocumentalNaoExigeMeasurements()
    {
        using HttpClient client = Client(new ResponseHandler((_, _) => Task.FromResult(JsonResponse(ReportDetail))));
        BackendLabClientLabResultsRepository repository = new(client);

        ClientReportDetail detail = Assert.IsType<ClientReportDetail>(
            await repository.GetReportDetailAsync("report-81", CancellationToken.None));

        Assert.Equal("report-81", detail.Analysis.Id);
        Assert.Equal("/api/client/reports/report-81/file", detail.FileUrl);
        Assert.Empty(detail.Analysis.Measurements);
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized, typeof(ClientLabAuthenticationRequiredException))]
    [InlineData(HttpStatusCode.Forbidden, typeof(ClientLabAccessForbiddenException))]
    [InlineData(HttpStatusCode.BadRequest, typeof(ClientLabRequestRejectedException))]
    [InlineData(HttpStatusCode.ServiceUnavailable, typeof(ClientLabSourceUnavailableException))]
    public async Task FalhasHttpDaFontePermanecemFalhas(HttpStatusCode status, Type exceptionType)
    {
        using HttpClient client = Client(new ResponseHandler((_, _) => Task.FromResult(new HttpResponseMessage(status))));
        BackendLabClientLabResultsRepository repository = new(client);

        Exception failure = await Assert.ThrowsAnyAsync<Exception>(() => repository.GetEquipmentAsync(
            "eq-81", CancellationToken.None));

        Assert.Equal(exceptionType, failure.GetType());
    }

    [Fact]
    public async Task NotFoundDeRecursoNaoEConvertidoEmListaVazia()
    {
        using HttpClient client = Client(new ResponseHandler((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound))));
        BackendLabClientLabResultsRepository repository = new(client);

        Assert.Null(await repository.GetEquipmentAsync("eq-81", CancellationToken.None));
        Assert.Null(await repository.ListAnalysesOfEquipmentAsync("eq-81", new PageRequest(1, 20), CancellationToken.None));
    }

    [Fact]
    public async Task BearerEObtidoPorRequisicaoSemVazamentoEntreChamadasConcorrentes()
    {
        ConcurrentBag<string?> receivedTokens = [];
        ResponseHandler server = new(async (request, cancellationToken) =>
        {
            receivedTokens.Add(request.Headers.Authorization?.ToString());
            await Task.Delay(10, cancellationToken);
            return JsonResponse(EquipmentPage);
        });
        HttpContextAccessor context = new();
        SpringBearerForwardingHandler forwarder = new(context) { InnerHandler = server };
        using HttpClient client = new(forwarder) { BaseAddress = new Uri("https://spring.test/") };
        BackendLabClientLabResultsRepository repository = new(client);

        async Task CallWithToken(string token)
        {
            context.HttpContext = new DefaultHttpContext();
            context.HttpContext.Request.Headers.Authorization = $"Bearer {token}";
            await repository.ListEquipmentAsync(new PageRequest(1, 20), CancellationToken.None);
        }

        await Task.WhenAll(Task.Run(() => CallWithToken("token-one")), Task.Run(() => CallWithToken("token-two")));

        Assert.Equal(2, receivedTokens.Count);
        Assert.Contains("Bearer token-one", receivedTokens);
        Assert.Contains("Bearer token-two", receivedTokens);
    }

    private static HttpClient Client(HttpMessageHandler handler) => new(handler)
    {
        BaseAddress = new Uri("https://spring.test/")
    };

    private static HttpResponseMessage JsonResponse(string json) => new(HttpStatusCode.OK)
    {
        Content = new StringContent(json, Encoding.UTF8, "application/json")
    };

    private const string EquipmentPage = """
        {"items":[{"id":"eq-opaque-81","company":{"id":"company-1","name":"Empresa"},"serialNumber":"S-81","tag":"TAG-81","type":"TRANSFORMADOR","substation":"SE-81","primaryVoltageKv":138.125,"manufacturer":"Fabricante","manufactureYear":2001,"powerMva":50.250,"oilType":"Mineral","oilVolumeLiters":1000.5,"publishedAnalysisCount":1,"latestPublishedAnalysis":{"id":"report-81","reportId":"report-81","testId":"test-11","testCode":null,"reportCode":"REL-81","testType":"Análise livre","analysisType":"chromatography","scheduledAt":"2026-11-13","validatedAt":"2026-11-13T20:00:00-03:00","publishedAt":"2026-11-14T01:00:00Z","technicalClassification":"Normal","conclusion":"Conclusão do snapshot","pdfAvailable":true}}],"page":1,"pageSize":20,"totalItems":1}
        """;

    private const string AnalysisPage = """
        {"items":[{"id":"report-81","reportId":"report-81","testId":"test-11","testCode":"TEST-11","reportCode":"REL-81","company":{"id":"company-1","name":"Empresa"},"quote":{"id":"quote-1","protocol":"PROTOCOLO-REPETIVEL"},"sample":{"id":"sample-1","code":"A-81","name":"Amostra"},"equipment":{"id":"eq-81","serialNumber":"S-81","tag":"TAG-81","type":"TRANSFORMADOR"},"testType":"Análise cromatográfica","analysisType":"chromatography","scheduledAt":"2026-11-13","validatedAt":"2026-11-13T20:00:00-03:00","publishedAt":"2026-11-14T01:00:00Z","technicalClassification":"Normal","conclusion":"Resultado do snapshot","pdfAvailable":true,"measurements":[{"code":"neutralization-index","name":"Índice de Neutralização","unit":"mg KOH/g","method":"NBR 14248","referenceLimit":null,"result":{"kind":"quantity","value":"12.340"}},{"code":"h2","name":"Hidrogênio","unit":"ppm","method":null,"referenceLimit":null,"result":{"kind":"not-detected"}},{"code":"co","name":"Monóxido de Carbono","unit":"ppm","method":null,"referenceLimit":null,"result":{"kind":"not-performed"}},{"code":"new-code","name":"Resultado novo","unit":"uX","method":"M-1","referenceLimit":"limite textual","result":{"kind":"text","text":"< 0,01"}}]}],"page":1,"pageSize":20,"totalItems":1}
        """;

    private const string ReportDetail = """
        {"id":"report-81","reportId":"report-81","analysisId":"report-81","testId":"test-11","testCode":"TEST-11","reportCode":"REL-81","company":{"id":"company-1","name":"Empresa"},"quote":{"id":"quote-1","protocol":"P-81"},"sample":{"id":"sample-1","code":"A-81","name":"Amostra"},"equipment":{"id":"eq-81","serialNumber":"S-81","tag":"TAG-81","type":null},"testType":"Análise cromatográfica","analysisType":"chromatography","scheduledAt":"2026-11-13","validatedAt":"2026-11-13T20:00:00-03:00","publishedAt":"2026-11-14T01:00:00Z","technicalClassification":null,"conclusion":null,"pdfAvailable":true,"fileUrl":"/api/client/reports/report-81/file"}
        """;

    private sealed class ResponseHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> respond) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken) => respond(request, cancellationToken);
    }
}
