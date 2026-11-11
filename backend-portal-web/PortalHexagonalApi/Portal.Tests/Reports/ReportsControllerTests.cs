using System.Net;
using System.Net.Http.Headers;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

using Portal.Application.Reports;
using Portal.Domain.Reports;

using Xunit;

namespace Portal.Tests.Reports;

public sealed class ReportsControllerTests
{
    private static readonly PublishedReport Published = new(
        "804",
        "804",
        "L-45490-2026-X",
        "AM-2026-0142 - Óleo isolante",
        "Laboratório de Ensaios",
        DateTimeOffset.Parse("2026-11-08T01:07:00Z"),
        DateTimeOffset.Parse("2026-11-08T01:07:00Z"));

    private static readonly PublishedReport LegacyPublished = new(
        "relatorio-lactec",
        "LACTEC-00307/2026",
        "00307/2026",
        "Transformador TR-01",
        "Lab. de Óleos",
        DateTimeOffset.Parse("2026-03-07T00:00:00Z"));

    [Fact]
    public async Task RotasMantemContratoDeListaOrcamentoEArquivo()
    {
        using ReportsPortal portal = new(new PublishedReports());
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "sessao-ativa");

        using HttpResponseMessage listResponse = await client.GetAsync("/api/reports");
        JsonArray list = JsonNode.Parse(await listResponse.Content.ReadAsStringAsync())!.AsArray();
        Assert.Equal(HttpStatusCode.OK, listResponse.StatusCode);
        Assert.Equal(2, list.Count);
        Assert.Equal("L-45490-2026-X", (string?)list[0]!["protocol"]);
        Assert.Equal("Laboratório de Ensaios", (string?)list[0]!["laboratory"]);
        Assert.Equal("804", (string?)list[0]!["code"]);
        Assert.Equal("AM-2026-0142 - Óleo isolante", (string?)list[0]!["sample"]);
        Assert.Equal("07/11/2026", (string?)list[0]!["publishedOn"]);
        Assert.False(list[0]!.AsObject().ContainsKey("publishedAt"));
        Assert.False(list[1]!.AsObject().ContainsKey("publishedAt"));
        Assert.Equal("07/03/2026", (string?)list[1]!["publishedOn"]);

        using HttpResponseMessage quoteResponse = await client.GetAsync("/api/quotes/512/reports");
        JsonObject quote = JsonNode.Parse(await quoteResponse.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal(HttpStatusCode.OK, quoteResponse.StatusCode);
        Assert.Equal("ORC-2026-0091", (string?)quote["quoteCode"]);
        Assert.Equal("L-45490-2026-X", (string?)quote["protocol"]);
        Assert.Equal("Análise cromatográfica", (string?)quote["testType"]);
        Assert.Equal("1 relatório publicado disponível para consulta e download.", (string?)quote["summary"]);
        Assert.Equal((string?)list[0]!["publishedOn"], (string?)quote["reports"]![0]!["publishedOn"]);
        Assert.Equal("07/11/2026", (string?)quote["reports"]![0]!["publishedOn"]);
        Assert.False(quote["reports"]![0]!.AsObject().ContainsKey("publishedAt"));

        using HttpResponseMessage emptyQuoteResponse = await client.GetAsync("/api/quotes/without-reports/reports");
        Assert.Equal(HttpStatusCode.NoContent, emptyQuoteResponse.StatusCode);
        Assert.Empty(await emptyQuoteResponse.Content.ReadAsByteArrayAsync());

        using HttpResponseMessage fileResponse = await client.GetAsync("/api/reports/804/file");
        Assert.Equal(HttpStatusCode.OK, fileResponse.StatusCode);
        Assert.Equal("application/pdf", fileResponse.Content.Headers.ContentType?.MediaType);
        Assert.Equal("relatorio-804.pdf", fileResponse.Content.Headers.ContentDisposition?.FileNameStar
            ?? fileResponse.Content.Headers.ContentDisposition?.FileName?.Trim('"'));
        Assert.True(fileResponse.Headers.CacheControl?.NoStore);
        Assert.StartsWith("%PDF-1.7", await fileResponse.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task PdfAusenteRetorna404SemArquivoSubstituto()
    {
        using ReportsPortal portal = new(new PublishedReports(fileAvailable: false));
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "sessao-ativa");

        using HttpResponseMessage response = await client.GetAsync("/api/reports/804/file");
        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal("report-document-not-found", (string?)problem["code"]);
        Assert.NotEqual("application/pdf", response.Content.Headers.ContentType?.MediaType);
    }

    [Fact]
    public async Task RelatoriosExigemBearerAntesDaConsulta()
    {
        using ReportsPortal portal = new(new PublishedReports());
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.GetAsync("/api/reports");
        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("report-authentication-required", (string?)problem["code"]);
        Assert.Equal("Bearer", response.Headers.WwwAuthenticate.Single().Scheme);
    }

    private sealed class ReportsPortal(IForConsultingReports source) : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseSetting("LabResults:Source", "embedded-sample");
            builder.UseSetting("EquipmentRegistry:Source", "in-memory-sample");
            builder.UseSetting("Portal:DataSource", "backend-lab");
            builder.UseSetting("BackendLab:BaseUrl", "https://spring.test/");
            builder.ConfigureTestServices(services =>
            {
                services.RemoveAll<IForConsultingReports>();
                services.AddSingleton(source);
            });
        }
    }

    private sealed class PublishedReports(bool fileAvailable = true) : IForConsultingReports
    {
        public Task<IReadOnlyList<PublishedReport>> ListPublishedAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<PublishedReport>>([Published, LegacyPublished]);

        public Task<QuoteReportConclusion?> GetPublishedOfQuoteAsync(
            string quoteId,
            CancellationToken cancellationToken) =>
            Task.FromResult<QuoteReportConclusion?>(quoteId == "without-reports"
                ? new QuoteReportConclusion(quoteId, "ORC-2026-0000", string.Empty, string.Empty, "Nenhum relatório publicado disponível para consulta e download.", [])
                : new QuoteReportConclusion(
                    quoteId,
                    "ORC-2026-0091",
                    Published.Protocol,
                    "Análise cromatográfica",
                    "1 relatório publicado disponível para consulta e download.",
                    [Published]));

        public Task<PublishedReportFile?> GetFileAsync(string reportId, CancellationToken cancellationToken) =>
            Task.FromResult<PublishedReportFile?>(reportId == Published.Id && fileAvailable
                ? new PublishedReportFile(System.Text.Encoding.ASCII.GetBytes("%PDF-1.7\nrelatório persistido"))
                : null);
    }
}
