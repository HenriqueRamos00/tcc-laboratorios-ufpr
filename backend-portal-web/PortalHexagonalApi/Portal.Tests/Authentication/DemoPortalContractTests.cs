using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.AspNetCore.Hosting;
using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;
using Portal.Tests.Quotes;
using Xunit;

namespace Portal.Tests.Authentication;

public sealed class DemoPortalContractTests
{
    [Theory]
    [InlineData(FakeSalesforceQuoteRepository.EnergyCnpj, FakeSalesforceQuoteRepository.EnergyName)]
    [InlineData(FakeSalesforceQuoteRepository.DistributorCnpj, FakeSalesforceQuoteRepository.DistributorName)]
    public async Task EachClientSeesOnlyItsQuotesAndTheWholeAutoLabSample(string cnpj, string company)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(cnpj));
        using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using HttpClient client = portal.CreateClient();
        using (var scope = portal.Services.CreateScope())
            Assert.IsType<FakeSalesforceQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
        await Login(client);

        using var quoteList = await client.GetAsync("/api/quotes");
        Assert.Equal("9", Assert.Single(quoteList.Headers.GetValues("X-Total-Count")));
        JsonArray quotes = (await quoteList.Content.ReadFromJsonAsync<JsonArray>())!;
        string[] expected = cnpj == FakeSalesforceQuoteRepository.EnergyCnpj
            ? ["quote-1", "quote-3", "quote-6", "quote-8", "quote-10", "quote-12", "quote-14", "quote-16", "quote-18"]
            : ["quote-4", "quote-5", "quote-7", "quote-9", "quote-11", "quote-13", "quote-15", "quote-17", "quote-19"];
        Assert.Equal(expected.Order(StringComparer.Ordinal), quotes.Select(q => (string)q!["id"]!).Order(StringComparer.Ordinal));
        Assert.All(quotes, quote => Assert.Equal(company, (string?)quote!["companyName"]));
        string ownQuote = cnpj == FakeSalesforceQuoteRepository.EnergyCnpj ? "quote-3" : "quote-15";
        using var pdf = await client.GetAsync($"/api/quotes/{ownQuote}/document");
        Assert.Equal(HttpStatusCode.OK, pdf.StatusCode);
        Assert.Equal("application/pdf", pdf.Content.Headers.ContentType!.MediaType);
        byte[] documentBytes = await pdf.Content.ReadAsByteArrayAsync();
        Assert.StartsWith("%PDF-", Encoding.Latin1.GetString(documentBytes, 0, 5));
        Assert.Contains(company, PdfText.OfWholeDocument(documentBytes));

        JsonArray equipment = (await client.GetFromJsonAsync<JsonArray>("/api/equipments"))!;
        Assert.Equal(6, equipment.Count);
        Assert.Equal(
            [FakeSalesforceQuoteRepository.DistributorName, FakeSalesforceQuoteRepository.EnergyName],
            equipment.Select(item => (string)item!["company"]!).Distinct().Order(StringComparer.Ordinal));
        int totalReports = 0;
        foreach (JsonNode? item in equipment)
        {
            string id = (string)item!["id"]!;
            JsonArray reports = (await client.GetFromJsonAsync<JsonArray>($"/api/equipments/{id}/reports"))!;
            totalReports += reports.Count;
            using var detail = await client.GetAsync($"/api/equipments/{id}");
            Assert.Equal(HttpStatusCode.OK, detail.StatusCode);
            using var health = await client.GetAsync($"/api/equipments/{id}/health");
            Assert.Equal(HttpStatusCode.OK, health.StatusCode);
            using var report = await client.GetAsync($"/api/equipments/{id}/health/reports/{(string)reports[0]!["id"]!}");
            Assert.Equal(HttpStatusCode.OK, report.StatusCode);
        }
        Assert.Equal(11, totalReports);
        JsonArray allReports = (await client.GetFromJsonAsync<JsonArray>("/api/reports"))!;
        Assert.Equal(11, allReports.Count);
        // O AutoLAB guarda a forma nua (2026_45496); o orcamento de demonstracao publica o codigo
        // da cotacao (EAQ_2026_45496_V_1). O vinculo e a chave de laboratorio, e nao o texto inteiro.
        int linked = 0;
        foreach (JsonNode? quote in quotes)
        {
            string code = (string)quote!["code"]!;
            string? key = LaboratoryQuoteKey.FromQuotationCode(code)?.Value;
            Assert.NotNull(key);
            Assert.DoesNotContain(allReports, report => (string?)report!["quoteCode"] == code);
            int count = allReports.Count(report => (string?)report!["quoteCode"] == key);
            linked += count;
            using var response = await client.GetAsync($"/api/quotes/{(string)quote["id"]!}/reports");
            Assert.Equal(count == 0 ? HttpStatusCode.NoContent : HttpStatusCode.OK, response.StatusCode);
            if (count == 0) continue;
            JsonObject conclusion = (await response.Content.ReadFromJsonAsync<JsonObject>())!;
            JsonArray reports = conclusion["reports"]!.AsArray();
            Assert.Equal(count, reports.Count);
            Assert.Equal(code, (string?)conclusion["quoteCode"]);
            Assert.All(reports, report => Assert.Equal(key, (string?)report!["quoteCode"]));
        }
        Assert.True(linked > 0);
        JsonNode downloadable = allReports.First(report => (bool)report!["pdfAvailable"]!)!;
        string reportId = (string)downloadable["id"]!;
        using var signedDocument = await client.GetAsync($"/api/reports/{reportId}/document");
        using var compatibleFile = await client.GetAsync($"/api/reports/{reportId}/file");
        Assert.Equal(HttpStatusCode.OK, signedDocument.StatusCode);
        Assert.Equal(HttpStatusCode.OK, compatibleFile.StatusCode);
        Assert.Equal(await signedDocument.Content.ReadAsByteArrayAsync(), await compatibleFile.Content.ReadAsByteArrayAsync());

        string foreignQuote = cnpj == FakeSalesforceQuoteRepository.EnergyCnpj ? "quote-4" : "quote-3";
        foreach (string path in new[] { $"/api/quotes/{foreignQuote}", $"/api/quotes/{foreignQuote}/document", $"/api/quotes/{foreignQuote}/reports" })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        }
        Assert.Equal(1, backend.Count);
    }

    /// <summary>
    /// A massa local do AutoLAB guarda o OrcamentoCRM na forma nua, como o AutoLAB real; os
    /// orcamentos de demonstracao continuam recebendo a etapa do laboratorio pela chave do
    /// codigo da cotacao. Os dois lados (lista e detalhe) dizem a mesma etapa.
    /// </summary>
    [Fact]
    public async Task DemoQuotesKeepTheirLaboratoryStageFromTheBareAutoLabCodes()
    {
        Dictionary<string, string?> stages = [];
        foreach (string cnpj in new[] { FakeSalesforceQuoteRepository.EnergyCnpj, FakeSalesforceQuoteRepository.DistributorCnpj })
        {
            using var backend = new AuthHandler(HttpStatusCode.OK, Identity(cnpj));
            using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
            using HttpClient client = portal.CreateClient();
            await Login(client);
            foreach (JsonNode? quote in (await client.GetFromJsonAsync<JsonArray>("/api/quotes"))!)
            {
                string id = (string)quote!["id"]!;
                stages[id] = (string?)quote["laboratoryStage"];
                JsonObject detail = (await client.GetFromJsonAsync<JsonObject>($"/api/quotes/{id}"))!;
                Assert.Equal(stages[id], (string?)detail["laboratoryStage"]);
            }
        }

        Assert.Equal(18, stages.Count);
        Assert.Equal(
            ["quote-10=completo", "quote-11=validado", "quote-12=finalizado", "quote-13=elaborando-relatorio",
             "quote-14=relatorio-publicado", "quote-15=relatorio-publicado", "quote-9=em-execucao"],
            stages.Where(pair => pair.Value is not null).Select(pair => $"{pair.Key}={pair.Value}").Order(StringComparer.Ordinal));
    }

    [Theory]
    [InlineData("none")]
    [InlineData("unknown")]
    public async Task AClientWithoutAKnownCompanySeesNoQuote(string scope)
    {
        string[] cnpjs = scope == "none" ? [] : ["12ABC34501DE35"];
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(cnpjs));
        using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using HttpClient client = portal.CreateClient();
        await Login(client);

        Assert.Empty((await client.GetFromJsonAsync<JsonArray>("/api/quotes"))!);
    }

    [Fact]
    public async Task DemoEndpointsRequireAJwt()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(FakeSalesforceQuoteRepository.EnergyCnpj));
        using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using HttpClient client = portal.CreateClient();
        foreach (string path in new[] { "/api/quotes", "/api/quotes/quote-3/document", "/api/equipments", "/api/reports" })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        }
        Assert.Equal(0, backend.Count);
    }

    /// <summary>
    /// Com a demonstracao ligada, o Salesforce nem e registrado: o compose.auth.yaml sobe sem
    /// credenciais Salesforce.
    /// </summary>
    [Fact]
    public void DemoDataStartsWithoutSalesforceCredentials()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(FakeSalesforceQuoteRepository.EnergyCnpj));
        using var basePortal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using var portal = basePortal.WithWebHostBuilder(builder => builder
            .UseSetting("Salesforce:Url", "")
            .UseSetting("Salesforce:ClientId", "")
            .UseSetting("Salesforce:ClientSecret", ""));
        using HttpClient client = portal.CreateClient();
        using var scope = portal.Services.CreateScope();

        Assert.IsType<FakeSalesforceQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
        Assert.Null(portal.Services.GetService<ISalesforceTokenProvider>());
    }

    [Fact]
    public void DemoDataIsOptInAndNormalLactecKeepsSalesforce()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(FakeSalesforceQuoteRepository.EnergyCnpj));
        using var portal = new AuthPortal(backend);
        using var scope = portal.Services.CreateScope();
        Assert.IsType<SalesforceQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
    }

    private static string Identity(params string[] cnpjs) => JsonSerializer.Serialize(new
    {
        id = "42", name = "Cliente demo", email = "client@lactec.com", role = "CLIENTE",
        serviceCode = "portal-web", authorizations = new { companyCnpjs = cnpjs }
    });

    private static async Task Login(HttpClient client)
    {
        using var response = await client.PostAsJsonAsync("/auth/login", new { email = "client@lactec.com", password = "client@lactec.com" });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var body = (await response.Content.ReadFromJsonAsync<JsonObject>())!;
        client.DefaultRequestHeaders.Authorization = new("Bearer", (string)body["Token"]!);
    }
}
