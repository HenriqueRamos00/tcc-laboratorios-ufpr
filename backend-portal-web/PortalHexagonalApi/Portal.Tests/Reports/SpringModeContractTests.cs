using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.AutoLab;
using Portal.Adapters.Quotes;
using Portal.Adapters.Reports;
using Portal.Application.Quotes;
using Portal.Application.Reports;

using Xunit;

namespace Portal.Tests.Reports;

public sealed class SpringModeContractTests
{
    [Fact]
    public async Task SpringStartsWithoutAutoLabAndPreservesStagesReportsAndBothPdfRoutes()
    {
        using Origin origin = new();
        using SpringPortal portal = new(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "client-session");

        Assert.Null(portal.Services.GetService<AutoLabQueryLimits>());
        using HttpResponseMessage quoteResponse = await client.GetAsync("/api/quotes/52");
        Assert.Equal(HttpStatusCode.OK, quoteResponse.StatusCode);
        JsonNode quote = JsonNode.Parse(await quoteResponse.Content.ReadAsStringAsync())!;
        Assert.Equal("Em execução", (string?)quote["stage"]);
        Assert.Null(quote["laboratoryStage"]);

        using HttpResponseMessage listResponse = await client.GetAsync("/api/reports?page=1&pageSize=1");
        Assert.Equal(HttpStatusCode.OK, listResponse.StatusCode);
        Assert.Equal("1", listResponse.Headers.GetValues("X-Total-Count").Single());
        JsonNode report = JsonNode.Parse(await listResponse.Content.ReadAsStringAsync())![0]!;
        Assert.Equal("804", (string?)report["id"]);
        Assert.Equal("804", (string?)report["identification"]);
        Assert.Equal("Laboratório", (string?)report["laboratory"]);
        Assert.Equal("2026-11-07", (string?)report["publicationDate"]);
        Assert.True((bool)report["pdfAvailable"]!);
        Assert.False((bool)report["hasSignedDocument"]!);

        using HttpResponseMessage conclusion = await client.GetAsync("/api/quotes/52/reports?page=1&pageSize=1");
        Assert.Equal(HttpStatusCode.OK, conclusion.StatusCode);
        JsonNode result = JsonNode.Parse(await conclusion.Content.ReadAsStringAsync())!;
        Assert.Equal("ORC-52", (string?)result["quoteCode"]);
        Assert.Equal("Análise cromatográfica", (string?)result["testType"]);
        Assert.Equal("804", (string?)result["reports"]![0]!["id"]);

        foreach (string suffix in new[] { "file", "document" })
        {
            using HttpResponseMessage pdf = await client.GetAsync($"/api/reports/804/{suffix}");
            Assert.Equal(HttpStatusCode.OK, pdf.StatusCode);
            Assert.Equal("application/pdf", pdf.Content.Headers.ContentType?.MediaType);
            Assert.Equal("%PDF-1.7\npublished-spring-report", await pdf.Content.ReadAsStringAsync());
            Assert.True(pdf.Headers.CacheControl?.NoStore);
        }
        Assert.All(origin.Authorizations, value => Assert.Equal("Bearer client-session", value));
    }

    [Theory]
    [InlineData("/api/equipments")]
    [InlineData("/api/equipments/NTcxMjg5")]
    [InlineData("/api/equipments/NTcxMjg5/reports")]
    [InlineData("/api/equipments/NTcxMjg5/reports/cHJvdG9jb2xv/document")]
    [InlineData("/api/equipments/NTcxMjg5/health")]
    [InlineData("/api/equipments/NTcxMjg5/health/reports/cHJvdG9jb2xv")]
    public async Task PendingSpringEndpointsAreExplicitlyUnavailableWithoutAnyOriginRead(string route)
    {
        using Origin origin = new();
        using SpringPortal portal = new(origin);
        using HttpResponseMessage response = await portal.CreateClient().GetAsync(route);
        Assert.Equal(HttpStatusCode.NotImplemented, response.StatusCode);
        JsonNode problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!;
        Assert.Equal("lab-integration-pending", (string?)problem["code"]);
        Assert.Empty(origin.Authorizations);
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden)]
    [InlineData(HttpStatusCode.BadGateway)]
    public async Task SpringReportFailuresRemainFailuresWithoutDemoOrAutoLabFallback(HttpStatusCode status)
    {
        using Origin origin = new(status);
        using SpringPortal portal = new(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "client-session");
        using HttpResponseMessage response = await client.GetAsync("/api/reports");
        Assert.Equal(status, response.StatusCode);
        Assert.Single(origin.Authorizations);
    }

    private sealed class SpringPortal(Origin origin) : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseSetting("Portal:DataSource", "backend-lab");
            builder.UseSetting("BackendLab:BaseUrl", "https://spring.test/");
            foreach (string section in new[] { "LabResults", "EquipmentRegistry", "LaboratoryProgress", "ReportDocuments" })
                builder.UseSetting($"{section}:Source", "not-connected");
            builder.UseSetting("LabResults:Scope", "");
            builder.UseSetting("AutoLab:Server", "");
            builder.UseSetting("AutoLab:Password", "");
            builder.ConfigureTestServices(services =>
            {
                services.AddHttpClient<IQuoteRepository, SpringQuoteRepository>()
                    .ConfigurePrimaryHttpMessageHandler(() => origin);
                services.AddHttpClient<IReportDocumentRepository, SpringReportDocumentRepository>()
                    .ConfigurePrimaryHttpMessageHandler(() => origin);
            });
        }
    }

    private sealed class Origin(HttpStatusCode reportStatus = HttpStatusCode.OK) : HttpMessageHandler
    {
        private const string Report = """
            {"id":"804","code":"804","protocol":"P-52","sample":"AM-1","laboratory":"Laboratório","publishedOn":"2026-11-08T01:07:00Z"}
            """;
        public List<string?> Authorizations { get; } = [];

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Authorizations.Add(request.Headers.Authorization?.ToString());
            string path = request.RequestUri!.AbsolutePath;
            if (path == "/api/client/reports" && reportStatus != HttpStatusCode.OK)
                return Task.FromResult(new HttpResponseMessage(reportStatus));
            string json = path switch
            {
                "/api/client/reports" => $"[{Report}]",
                "/api/client/quotes/52" => """
                    {"id":"52","code":"ORC-52","name":"Análise","status":"InProgress","stage":"Em execução",
                    "description":"Óleo","companyName":"Empresa","protocol":"P-52","externalContactName":"",
                    "externalContactEmail":"","totalPrice":10,"createdDate":null}
                    """,
                "/api/client/quotes/52/reports" => $$"""
                    {"protocol":"P-52","testType":"Análise cromatográfica","summary":"Publicado","reports":[{{Report}}]}
                    """,
                "/api/client/reports/804/file" => "%PDF-1.7\npublished-spring-report",
                _ => throw new InvalidOperationException($"Unexpected origin read: {path}")
            };
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
            {
                Content = new StringContent(json, Encoding.UTF8, path.EndsWith("/file") ? "application/pdf" : "application/json")
            });
        }
    }
}
