using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.Extensions.DependencyInjection;
using Portal.Adapters.Demo;
using Portal.Adapters.Quotes;
using Portal.Api.LabResults;
using Portal.Application.Quotes;
using Xunit;

namespace Portal.Tests.Authentication;

public sealed class DemoPortalContractTests
{
    [Theory]
    [InlineData(DemoCompanies.EnergyCnpj, DemoCompanies.EnergyName, 4, 9)]
    [InlineData(DemoCompanies.DistributorCnpj, DemoCompanies.DistributorName, 2, 2)]
    public async Task EachClientSeesOnlyItsQuotesEquipmentAndReports(string cnpj, string company, int equipmentCount, int reportCount)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(cnpj));
        using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using HttpClient client = portal.CreateClient();
        using (var scope = portal.Services.CreateScope())
            Assert.IsType<FakeSalesforceQuoteRepository>(scope.ServiceProvider.GetRequiredService<IQuoteRepository>());
        await Login(client);

        JsonArray quotes = (await client.GetFromJsonAsync<JsonArray>("/api/quotes"))!;
        string[] expected = cnpj == DemoCompanies.EnergyCnpj
            ? ["quote-1", "quote-3", "quote-6", "quote-8", "quote-10", "quote-12", "quote-14", "quote-16", "quote-18"]
            : ["quote-4", "quote-5", "quote-7", "quote-9", "quote-11", "quote-13", "quote-15", "quote-17", "quote-19"];
        Assert.Equal(expected.Order(StringComparer.Ordinal), quotes.Select(q => (string)q!["id"]!).Order(StringComparer.Ordinal));
        Assert.All(quotes, quote => Assert.Equal(company, (string?)quote!["companyName"]));
        string ownQuote = cnpj == DemoCompanies.EnergyCnpj ? "quote-3" : "quote-4";
        using var pdf = await client.GetAsync($"/api/quotes/{ownQuote}/document");
        Assert.Equal(HttpStatusCode.OK, pdf.StatusCode);
        Assert.Equal("application/pdf", pdf.Content.Headers.ContentType!.MediaType);
        string document = Encoding.Latin1.GetString(await pdf.Content.ReadAsByteArrayAsync());
        Assert.StartsWith("%PDF-", document);
        Assert.Contains(company, document);

        JsonArray equipment = (await client.GetFromJsonAsync<JsonArray>("/api/equipments"))!;
        Assert.Equal(equipmentCount, equipment.Count);
        Assert.All(equipment, item => Assert.Equal(company, (string?)item!["company"]));
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
        Assert.Equal(reportCount, totalReports);

        string foreignQuote = cnpj == DemoCompanies.EnergyCnpj ? "quote-4" : "quote-3";
        string foreignEquipment = OpaqueIdentifier.Encode(cnpj == DemoCompanies.EnergyCnpj ? "36170301" : "571289");
        string foreignReport = OpaqueIdentifier.Encode(cnpj == DemoCompanies.EnergyCnpj ? "01718/2026-005" : "01417/2026-001");
        string ownEquipment = (string)equipment[0]!["id"]!;
        foreach (string path in new[]
        {
            $"/api/quotes/{foreignQuote}", $"/api/quotes/{foreignQuote}/document",
            $"/api/equipments/{foreignEquipment}", $"/api/equipments/{foreignEquipment}/reports",
            $"/api/equipments/{foreignEquipment}/health",
            $"/api/equipments/{foreignEquipment}/health/reports/{foreignReport}",
            $"/api/equipments/{ownEquipment}/health/reports/{foreignReport}"
        })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        }
        Assert.Equal(1, backend.Count);
    }

    [Theory]
    [InlineData("none")]
    [InlineData("unknown")]
    [InlineData("multiple")]
    public async Task UnsupportedDemoScopeNeverFallsBackToTheDevelopmentTenant(string scope)
    {
        string[] cnpjs = scope switch
        {
            "none" => [],
            "unknown" => ["12ABC34501DE35"],
            _ => [DemoCompanies.EnergyCnpj, DemoCompanies.DistributorCnpj]
        };
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(cnpjs));
        using var basePortal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using var portal = basePortal.WithWebHostBuilder(builder =>
            builder.UseSetting("LabResults:DevelopmentTenant", DemoCompanies.EnergyName));
        using HttpClient client = portal.CreateClient();
        await Login(client);
        foreach (string path in new[] { "/api/equipments", $"/api/equipments/{OpaqueIdentifier.Encode("571289")}/health" })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        }
        if (scope != "multiple")
            Assert.Empty((await client.GetFromJsonAsync<JsonArray>("/api/quotes"))!);
    }

    [Fact]
    public async Task DemoEndpointsRequireAJwt()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(DemoCompanies.EnergyCnpj));
        using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using HttpClient client = portal.CreateClient();
        foreach (string path in new[] { "/api/quotes", "/api/quotes/quote-3/document", "/api/equipments" })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        }
        Assert.Equal(0, backend.Count);
    }

    [Fact]
    public void DemoDataIsOptInAndNormalLactecKeepsSalesforce()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(DemoCompanies.EnergyCnpj));
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
