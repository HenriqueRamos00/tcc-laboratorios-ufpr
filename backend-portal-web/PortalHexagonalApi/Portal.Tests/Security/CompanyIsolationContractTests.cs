using System.Net;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;

using Portal.Api.LabResults;
using Portal.Tests.Authentication;
using Portal.Tests.AutoLab;
using Portal.Tests.LabResults;

using Xunit;

namespace Portal.Tests.Security;

/// <summary>
/// O que um cliente autenticado alcanca da outra empresa: nada. A suite entra pela porta da
/// frente, com o JWT do portal, sobre a massa do AutoLAB e os orcamentos de demonstracao, e
/// tenta cada rota como o cliente A com os identificadores do cliente B, que ela obtem
/// entrando como B. A resposta para o que e de outra empresa tem de ser a mesma do que nao
/// existe.
/// </summary>
public sealed class CompanyIsolationContractTests : IClassFixture<RunningPortal>
{
    private const string Energy = TestCompanies.EnergyCnpj;
    private const string Distributor = TestCompanies.DistributorCnpj;

    private const string EnergyQuote = "quote-18";
    private const string DistributorQuote = "quote-15";
    private const string DistributorQuoteCode = "2026_45496";

    private readonly RunningPortal _portal;

    public CompanyIsolationContractTests(RunningPortal portal)
    {
        _portal = portal;
    }

    public static TheoryData<string, string> EachCompanyAgainstTheOther => new()
    {
        { Energy, Distributor },
        { Distributor, Energy }
    };

    [Fact]
    public async Task EachCompanyListsOnlyItsOwnEquipmentAndReports()
    {
        using HttpClient energy = _portal.CreateClientOf(Energy);
        using HttpClient distributor = _portal.CreateClientOf(Distributor);

        JsonArray ofEnergy = await ListAsync(energy, "/api/equipments");
        JsonArray ofDistributor = await ListAsync(distributor, "/api/equipments");
        JsonArray reportsOfEnergy = await ListAsync(energy, "/api/reports");
        JsonArray reportsOfDistributor = await ListAsync(distributor, "/api/reports");

        Assert.Equal(["111.594/1", "571289", "884120", "884121"], Texts(ofEnergy, "serialNumber").Order(StringComparer.Ordinal));
        Assert.Equal(["36170301", "5233"], Texts(ofDistributor, "serialNumber").Order(StringComparer.Ordinal));
        Assert.All(ofEnergy, equipment => Assert.Equal(TestCompanies.Energy, (string?)equipment!["company"]));
        Assert.All(ofDistributor, equipment => Assert.Equal(TestCompanies.Distributor, (string?)equipment!["company"]));
        Assert.Empty(Texts(ofEnergy, "id").Intersect(Texts(ofDistributor, "id")));

        Assert.Equal(9, reportsOfEnergy.Count);
        Assert.Equal(2, reportsOfDistributor.Count);
        Assert.All(reportsOfEnergy, report => Assert.Equal(TestCompanies.Energy, (string?)report!["client"]));
        Assert.All(reportsOfDistributor, report => Assert.Equal(TestCompanies.Distributor, (string?)report!["client"]));
        Assert.Empty(Texts(reportsOfEnergy, "id").Intersect(Texts(reportsOfDistributor, "id")));
    }

    [Fact]
    public async Task ASessionOfBothCompaniesSeesTheUnionAndNothingElse()
    {
        using HttpClient both = _portal.CreateClientOf(Energy, Distributor);

        Assert.Equal(6, (await ListAsync(both, "/api/equipments")).Count);
        Assert.Equal(11, (await ListAsync(both, "/api/reports")).Count);
        Assert.Equal(18, (await ListAsync(both, "/api/quotes")).Count);
    }

    [Theory]
    [MemberData(nameof(EachCompanyAgainstTheOther))]
    public async Task TheEquipmentOfAnotherCompanyAnswersExactlyLikeOneThatDoesNotExist(string owner, string intruder)
    {
        using HttpClient ownerClient = _portal.CreateClientOf(owner);
        using HttpClient intruderClient = _portal.CreateClientOf(intruder);

        string missingEquipment = OpaqueIdentifier.Encode("NAO-EXISTE-0001");
        string missingReport = OpaqueIdentifier.Encode("99999/2099-999");

        foreach (string equipment in Texts(await ListAsync(ownerClient, "/api/equipments"), "id"))
        {
            JsonArray reports = await ListAsync(ownerClient, $"/api/equipments/{equipment}/reports");
            string report = (string)reports[0]!["id"]!;

            (string Foreign, string Missing)[] routes =
            [
                ($"/api/equipments/{equipment}", $"/api/equipments/{missingEquipment}"),
                ($"/api/equipments/{equipment}/reports", $"/api/equipments/{missingEquipment}/reports"),
                ($"/api/equipments/{equipment}/health", $"/api/equipments/{missingEquipment}/health"),
                ($"/api/equipments/{equipment}/health/reports/{report}",
                    $"/api/equipments/{missingEquipment}/health/reports/{missingReport}"),
                ($"/api/equipments/{equipment}/reports/{report}/document",
                    $"/api/equipments/{missingEquipment}/reports/{missingReport}/document")
            ];

            foreach ((string foreign, string missing) in routes)
            {
                using HttpResponseMessage allowed = await ownerClient.GetAsync(foreign);
                using HttpResponseMessage refused = await intruderClient.GetAsync(foreign);
                using HttpResponseMessage absent = await intruderClient.GetAsync(missing);

                if (!foreign.EndsWith("/document", StringComparison.Ordinal))
                    Assert.Equal(HttpStatusCode.OK, allowed.StatusCode);

                Assert.Equal(HttpStatusCode.NotFound, refused.StatusCode);
                Assert.Equal(HttpStatusCode.NotFound, absent.StatusCode);
                Assert.Equal(await ProblemOf(absent), await ProblemOf(refused));
                Assert.True(refused.Headers.CacheControl?.NoStore);
            }
        }
    }

    [Theory]
    [MemberData(nameof(EachCompanyAgainstTheOther))]
    public async Task TheSignedReportOfAnotherCompanyIsNotDownloadedByGuessingItsAddress(string owner, string intruder)
    {
        using HttpClient ownerClient = _portal.CreateClientOf(owner);
        using HttpClient intruderClient = _portal.CreateClientOf(intruder);

        int downloaded = 0;

        foreach (string equipment in Texts(await ListAsync(ownerClient, "/api/equipments"), "id"))
        {
            foreach (JsonNode? report in await ListAsync(ownerClient, $"/api/equipments/{equipment}/reports"))
            {
                if (!(bool)report!["hasSignedDocument"]!) continue;

                string route = $"/api/equipments/{equipment}/reports/{(string)report["id"]!}/document";

                using HttpResponseMessage allowed = await ownerClient.GetAsync(route);
                using HttpResponseMessage refused = await intruderClient.GetAsync(route);

                Assert.Equal(HttpStatusCode.OK, allowed.StatusCode);
                Assert.Equal("application/pdf", allowed.Content.Headers.ContentType?.MediaType);
                Assert.True(allowed.Headers.CacheControl?.NoStore);
                Assert.Equal(HttpStatusCode.NotFound, refused.StatusCode);
                Assert.Equal("application/problem+json", refused.Content.Headers.ContentType?.MediaType);
                downloaded++;
            }
        }

        Assert.True(downloaded > 0);
    }

    [Theory]
    [MemberData(nameof(EachCompanyAgainstTheOther))]
    public async Task OwnEquipmentDoesNotOpenTheReportOfAnotherCompany(string owner, string intruder)
    {
        using HttpClient ownerClient = _portal.CreateClientOf(owner);
        using HttpClient intruderClient = _portal.CreateClientOf(intruder);

        string foreignEquipment = Texts(await ListAsync(ownerClient, "/api/equipments"), "id")[0];
        string foreignReport = (string)(await ListAsync(ownerClient, $"/api/equipments/{foreignEquipment}/reports"))[0]!["id"]!;
        string ownEquipment = Texts(await ListAsync(intruderClient, "/api/equipments"), "id")[0];

        using HttpResponseMessage document = await intruderClient
            .GetAsync($"/api/equipments/{ownEquipment}/reports/{foreignReport}/document");
        string overview = await intruderClient.GetStringAsync($"/api/equipments/{ownEquipment}/health");
        using HttpResponseMessage selected = await intruderClient
            .GetAsync($"/api/equipments/{ownEquipment}/health/reports/{foreignReport}");

        Assert.Equal(HttpStatusCode.NotFound, document.StatusCode);
        Assert.Equal(HttpStatusCode.OK, selected.StatusCode);
        Assert.Equal(overview, await selected.Content.ReadAsStringAsync());
    }

    [Theory]
    [MemberData(nameof(EachCompanyAgainstTheOther))]
    public async Task TheReportOfAnotherCompanyAnswersExactlyLikeOneThatDoesNotExist(string owner, string intruder)
    {
        using HttpClient ownerClient = _portal.CreateClientOf(owner);
        using HttpClient intruderClient = _portal.CreateClientOf(intruder);

        string missing = OpaqueIdentifier.Encode(Guid.NewGuid().ToString("D"));
        JsonArray reports = await ListAsync(ownerClient, "/api/reports");

        Assert.Contains(reports, report => (bool)report!["hasSignedDocument"]!);

        foreach (JsonNode? report in reports)
        {
            string id = (string)report!["id"]!;

            foreach (string suffix in new[] { "document", "file" })
            {
                using HttpResponseMessage allowed = await ownerClient.GetAsync($"/api/reports/{id}/{suffix}");
                using HttpResponseMessage refused = await intruderClient.GetAsync($"/api/reports/{id}/{suffix}");
                using HttpResponseMessage absent = await intruderClient.GetAsync($"/api/reports/{missing}/{suffix}");

                Assert.Equal(
                    (bool)report["hasSignedDocument"]! ? HttpStatusCode.OK : HttpStatusCode.NotFound,
                    allowed.StatusCode);
                Assert.Equal(HttpStatusCode.NotFound, refused.StatusCode);
                Assert.Equal(await ProblemOf(absent), await ProblemOf(refused));
                Assert.True(refused.Headers.CacheControl?.NoStore);
            }
        }
    }

    [Fact]
    public async Task TheQuoteCodeOfAnotherCompanyFiltersNothingIntoTheList()
    {
        using HttpClient energy = _portal.CreateClientOf(Energy);
        using HttpClient distributor = _portal.CreateClientOf(Distributor);

        using HttpResponseMessage refused = await energy.GetAsync($"/api/reports?quoteCode={DistributorQuoteCode}");
        JsonArray allowed = await ListAsync(distributor, $"/api/reports?quoteCode={DistributorQuoteCode}");

        Assert.Equal(HttpStatusCode.OK, refused.StatusCode);
        Assert.Equal("0", string.Join(",", refused.Headers.GetValues("X-Total-Count")));
        Assert.Empty(JsonNode.Parse(await refused.Content.ReadAsStringAsync())!.AsArray());
        Assert.Single(allowed);
    }

    [Fact]
    public async Task TheQuoteOfAnotherCompanyAnswersExactlyLikeOneThatDoesNotExist()
    {
        using HttpClient energy = _portal.CreateClientOf(Energy);
        using HttpClient distributor = _portal.CreateClientOf(Distributor);

        JsonArray ofEnergy = await ListAsync(energy, "/api/quotes");
        JsonArray ofDistributor = await ListAsync(distributor, "/api/quotes");

        Assert.Equal(9, ofEnergy.Count);
        Assert.Equal(9, ofDistributor.Count);
        Assert.Empty(Texts(ofEnergy, "id").Intersect(Texts(ofDistributor, "id")));
        Assert.Contains(DistributorQuote, Texts(ofDistributor, "id"));

        foreach (string suffix in new[] { "", "/document", "/reports" })
        {
            using HttpResponseMessage allowed = await distributor.GetAsync($"/api/quotes/{DistributorQuote}{suffix}");
            using HttpResponseMessage refused = await energy.GetAsync($"/api/quotes/{DistributorQuote}{suffix}");
            using HttpResponseMessage absent = await energy.GetAsync($"/api/quotes/quote-que-nao-existe{suffix}");

            Assert.Equal(HttpStatusCode.OK, allowed.StatusCode);
            Assert.Equal(HttpStatusCode.NotFound, refused.StatusCode);
            Assert.Equal(await ProblemOf(absent), await ProblemOf(refused));
            Assert.True(refused.Headers.CacheControl?.NoStore);
        }
    }

    [Fact]
    public async Task TheReportsOfAQuoteAreOnlyTheOnesOfTheCompanyOfTheSession()
    {
        using HttpClient energy = _portal.CreateClientOf(Energy);

        JsonObject conclusion = JsonNode.Parse(
            await energy.GetStringAsync($"/api/quotes/{EnergyQuote}/reports"))!.AsObject();

        Assert.NotEmpty(conclusion["reports"]!.AsArray());
        Assert.All(conclusion["reports"]!.AsArray(), report =>
            Assert.Equal(TestCompanies.Energy, (string?)report!["client"]));
    }

    public static TheoryData<string> SessionsWithoutAUsableCompany => new()
    {
        "sem-claim",
        "lista-vazia",
        "texto-vazio",
        "curinga",
        "todas",
        "mascarado",
        "valido-e-malformado",
        "dois-cnpjs-num-texto",
        "empresa-desconhecida"
    };

    [Theory]
    [MemberData(nameof(SessionsWithoutAUsableCompany))]
    public async Task ASessionWithoutAUsableCompanySeesNothingAndNeverEverything(string session)
    {
        object? claim = session switch
        {
            "sem-claim" => null,
            "lista-vazia" => Array.Empty<string>(),
            "texto-vazio" => "",
            "curinga" => "*",
            "todas" => "all",
            "mascarado" => "30.960.572/0001-83",
            "valido-e-malformado" => new[] { Energy, "30.960.572/0001-83" },
            "dois-cnpjs-num-texto" => $"{Energy},{Distributor}",
            _ => new[] { "55444333000122" }
        };

        using HttpClient owner = _portal.CreateClientOf(Energy);
        using HttpClient client = _portal.CreateClientWithToken(PortalSessions.TokenWithClaim(claim));

        string equipment = Texts(await ListAsync(owner, "/api/equipments"), "id")[0];
        string report = (string)(await ListAsync(owner, $"/api/equipments/{equipment}/reports"))[0]!["id"]!;
        string signedReport = (string)(await ListAsync(owner, "/api/reports"))
            .First(item => (bool)item!["hasSignedDocument"]!)!["id"]!;

        foreach (string list in new[] { "/api/equipments", "/api/reports", "/api/quotes", "/api/equipments?search=57" })
        {
            using HttpResponseMessage response = await client.GetAsync(list);

            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
            Assert.Equal("0", string.Join(",", response.Headers.GetValues("X-Total-Count")));
            Assert.Empty(JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray());
        }

        foreach (string route in new[]
        {
            $"/api/equipments/{equipment}",
            $"/api/equipments/{equipment}/reports",
            $"/api/equipments/{equipment}/health",
            $"/api/equipments/{equipment}/health/reports/{report}",
            $"/api/equipments/{equipment}/reports/{report}/document",
            $"/api/reports/{signedReport}/document",
            $"/api/reports/{signedReport}/file",
            $"/api/quotes/{EnergyQuote}",
            $"/api/quotes/{EnergyQuote}/document",
            $"/api/quotes/{EnergyQuote}/reports"
        })
        {
            using HttpResponseMessage response = await client.GetAsync(route);

            Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        }
    }

    [Theory]
    [InlineData("/api/equipments")]
    [InlineData("/api/reports")]
    [InlineData("/api/quotes")]
    public async Task NothingTheClientSendsWidensWhatTheTokenAllows(string list)
    {
        using HttpClient plain = _portal.CreateClientOf(Energy);
        using HttpClient insistent = _portal.CreateClientOf(Energy);

        (string Name, string Value)[] attempts =
        [
            ("companyCnpjs", Distributor),
            ("companyCnpj", Distributor),
            ("cnpj", Distributor),
            ("Cnpj", "11.222.333/0001-81"),
            ("company", TestCompanies.Distributor),
            ("empresa", TestCompanies.Distributor),
            ("inquilino", TestCompanies.Distributor),
            ("tenant", TestCompanies.Distributor),
            ("scope", "all"),
            ("Scope", "all"),
            ("clienteSolicitante", TestCompanies.Distributor),
            ("EmpresaTrafo", TestCompanies.Distributor)
        ];

        foreach ((string name, string value) in attempts)
        {
            insistent.DefaultRequestHeaders.TryAddWithoutValidation(name, value);
            insistent.DefaultRequestHeaders.TryAddWithoutValidation("X-" + name, value);
        }

        insistent.DefaultRequestHeaders.TryAddWithoutValidation("X-Company-Cnpjs", Distributor);
        insistent.DefaultRequestHeaders.TryAddWithoutValidation("X-Tenant-Id", Distributor);
        insistent.DefaultRequestHeaders.TryAddWithoutValidation("X-Forwarded-User", "client2@lactec.com");
        insistent.DefaultRequestHeaders.TryAddWithoutValidation("Cookie", $"companyCnpjs={Distributor}; scope=all");

        string query = string.Join("&", attempts.Select(attempt =>
            $"{Uri.EscapeDataString(attempt.Name)}={Uri.EscapeDataString(attempt.Value)}"));

        string[] expected = Texts(await ListAsync(plain, list), "id");
        string[] found = Texts(await ListAsync(insistent, $"{list}?{query}&companyCnpjs={Energy}&companyCnpjs={Distributor}"), "id");

        Assert.NotEmpty(expected);
        Assert.Equal(expected, found);
    }

    [Fact]
    public async Task NothingTheClientSendsOpensTheRecordOfAnotherCompany()
    {
        using HttpClient distributor = _portal.CreateClientOf(Distributor);
        using HttpClient insistent = _portal.CreateClientOf(Energy);

        insistent.DefaultRequestHeaders.TryAddWithoutValidation("X-Company-Cnpjs", Distributor);
        insistent.DefaultRequestHeaders.TryAddWithoutValidation("companyCnpjs", Distributor);

        string equipment = Texts(await ListAsync(distributor, "/api/equipments"), "id")[0];
        string report = (string)(await ListAsync(distributor, $"/api/equipments/{equipment}/reports"))[0]!["id"]!;
        string signedReport = (string)(await ListAsync(distributor, "/api/reports"))
            .First(item => (bool)item!["hasSignedDocument"]!)!["id"]!;
        string query = $"?companyCnpjs={Distributor}&cnpj={Distributor}&scope=all&inquilino={Uri.EscapeDataString(TestCompanies.Distributor)}";

        foreach (string route in new[]
        {
            $"/api/equipments/{equipment}",
            $"/api/equipments/{equipment}/reports",
            $"/api/equipments/{equipment}/health",
            $"/api/equipments/{equipment}/health/reports/{report}",
            $"/api/equipments/{equipment}/reports/{report}/document",
            $"/api/reports/{signedReport}/document",
            $"/api/quotes/{DistributorQuote}",
            $"/api/quotes/{DistributorQuote}/document",
            $"/api/quotes/{DistributorQuote}/reports"
        })
        {
            using HttpResponseMessage response = await insistent.GetAsync(route + query);

            Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        }
    }

    [Fact]
    public async Task TheRemovedScopeSettingsNoLongerWidenAnything()
    {
        using WebApplicationFactory<Program> misconfigured = _portal.WithWebHostBuilder(builder => builder
            .UseSetting("LabResults:Scope", "all")
            .UseSetting("LabResults:DevelopmentTenant", TestCompanies.Distributor));
        using HttpClient energy = misconfigured.CreateClientOf(Energy);
        using HttpClient nobody = misconfigured.CreateClientOf();
        using HttpClient anonymous = misconfigured.CreateClientWithToken(null);

        Assert.Equal(4, (await ListAsync(energy, "/api/equipments")).Count);
        Assert.Empty(await ListAsync(nobody, "/api/equipments"));
        Assert.Empty(await ListAsync(nobody, "/api/reports"));

        using HttpResponseMessage unauthenticated = await anonymous.GetAsync("/api/equipments");

        Assert.Equal(HttpStatusCode.Unauthorized, unauthenticated.StatusCode);
    }

    [Theory]
    [InlineData("/api/equipments")]
    [InlineData("/api/equipments/NTcxMjg5")]
    [InlineData("/api/equipments/NTcxMjg5/reports")]
    [InlineData("/api/equipments/NTcxMjg5/health")]
    [InlineData("/api/equipments/NTcxMjg5/health/reports/MDE0MTcvMjAyNi0wMDE")]
    [InlineData("/api/equipments/NTcxMjg5/reports/MDE0MTcvMjAyNi0wMDE/document")]
    [InlineData("/api/reports")]
    [InlineData("/api/reports/MTlkNmIwZDAtYzkwZC00YzEwLTgwYjctNzAyZmY5ZjU4Mzll/document")]
    [InlineData("/api/quotes")]
    [InlineData("/api/quotes/quote-18")]
    [InlineData("/api/quotes/quote-18/document")]
    [InlineData("/api/quotes/quote-18/reports")]
    public async Task EveryRouteRefusesARequestWithoutTheSessionOfTheClient(string route)
    {
        using HttpClient anonymous = _portal.CreateClientWithToken(null);
        using HttpClient forged = _portal.CreateClientWithToken(
            PortalSessions.TokenOf(Energy)[..^4] + "AAAA");

        using HttpResponseMessage withoutToken = await anonymous.GetAsync(route);
        using HttpResponseMessage withForgedToken = await forged.GetAsync(route);

        Assert.Equal(HttpStatusCode.Unauthorized, withoutToken.StatusCode);
        Assert.Equal(HttpStatusCode.Unauthorized, withForgedToken.StatusCode);
    }

    [Fact]
    public async Task TheListsAnswerWithNoStoreAndWithoutEchoingTheCnpj()
    {
        using HttpClient energy = _portal.CreateClientOf(Energy);

        foreach (string route in new[] { "/api/equipments", "/api/reports" })
        {
            using HttpResponseMessage response = await energy.GetAsync(route);
            string body = await response.Content.ReadAsStringAsync();

            Assert.True(response.Headers.CacheControl?.NoStore);
            Assert.DoesNotContain(Energy, body, StringComparison.Ordinal);
            Assert.DoesNotContain("30.960.572/0001-83", body, StringComparison.Ordinal);
            Assert.DoesNotContain(Energy, response.Headers.ToString(), StringComparison.Ordinal);
        }
    }

    private static async Task<JsonArray> ListAsync(HttpClient client, string route)
    {
        using HttpResponseMessage response = await client.GetAsync(route);

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
    }

    private static string[] Texts(JsonArray items, string field)
    {
        return [.. items.Select(item => (string)item![field]!)];
    }

    private static async Task<string> ProblemOf(HttpResponseMessage response)
    {
        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        problem.Remove("traceId");

        return $"{(int)response.StatusCode} {response.Content.Headers.ContentType?.MediaType} {problem.ToJsonString()}";
    }
}
