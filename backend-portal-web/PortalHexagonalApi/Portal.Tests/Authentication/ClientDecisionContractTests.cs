using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Adapters.Quotes;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;
using Portal.Tests.Quotes;

using Xunit;

namespace Portal.Tests.Authentication;

public sealed class ClientDecisionContractTests
{
    [Fact]
    public async Task OAceiteEGravadoComAutorEApareceNaListagemDeOrcamentos()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var created = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", Terms());

        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        Assert.Equal("acceptance", (string?)(await created.Content.ReadFromJsonAsync<JsonObject>())!["kind"]);
        ClientDecision saved = Assert.Single(store.Recorded);
        Assert.Equal(("quote-3", "42", "Marina Costa"), (saved.QuoteId, saved.Author.UserId, saved.Author.Name));
        Assert.Equal("Rua das Flores, 10", saved.Terms!.Billing.Street);
        Assert.Equal(["financeiro@empresa.com", "fiscal@empresa.com"], saved.Terms.Billing.InvoiceEmails);

        JsonArray quotes = (await client.GetFromJsonAsync<JsonArray>("/api/quotes"))!;
        JsonNode quote = quotes.Single(item => (string?)item!["id"] == "quote-3")!;
        Assert.Equal("acceptance", (string?)quote["clientDecision"]!["kind"]);
        Assert.All(quotes.Where(item => (string?)item!["id"] != "quote-3"), item =>
            Assert.Null(item!["clientDecision"]));
    }

    [Fact]
    public async Task UmSegundoAceiteOuOEnvioAntesDoAceiteRespondemConflito()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var shipmentFirst = await client.PostAsync("/api/quotes/quote-3/sample-shipment", null);
        using var accepted = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", Terms());
        using var again = await client.PostAsJsonAsync("/api/quotes/quote-3/rejection", new { justification = "mudamos de ideia" });
        using var shipment = await client.PostAsync("/api/quotes/quote-3/sample-shipment", null);

        Assert.Equal(HttpStatusCode.Conflict, shipmentFirst.StatusCode);
        Assert.Equal(HttpStatusCode.Created, accepted.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, again.StatusCode);
        Assert.Equal("client-decision-not-allowed", (string?)(await again.Content.ReadFromJsonAsync<JsonObject>())!["code"]);
        Assert.Equal(HttpStatusCode.Created, shipment.StatusCode);
    }

    [Fact]
    public async Task AOrdemDeCompraPrecisaSerPdfEFicaGuardadaComOArquivo()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var notPdf = await client.PostAsync("/api/quotes/quote-3/purchase-order", FileForm("oc.pdf", "nao sou pdf"u8.ToArray()));
        byte[] pdf = "%PDF-1.7 ordem de compra"u8.ToArray();
        using var created = await client.PostAsync("/api/quotes/quote-3/purchase-order", FileForm("../../oc final.pdf", pdf));

        Assert.Equal(HttpStatusCode.BadRequest, notPdf.StatusCode);
        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        ClientDecision saved = Assert.Single(store.Recorded);
        Assert.Equal("oc final.pdf", saved.PurchaseOrder!.FileName);
        Assert.Equal(pdf, saved.PurchaseOrder.Content);
    }

    [Fact]
    public async Task TermoIncompletoEOrcamentoDeOutraEmpresaNaoGravamNada()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var incomplete = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance",
            new { billing = new { company = "EMPRESA" }, approval = new { allowsResultsByEmail = true } });
        using var otherCompany = await client.PostAsJsonAsync("/api/quotes/quote-5/acceptance", Terms());

        Assert.Equal(HttpStatusCode.BadRequest, incomplete.StatusCode);
        Assert.Equal("invalid-client-decision", (string?)(await incomplete.Content.ReadFromJsonAsync<JsonObject>())!["code"]);
        Assert.Equal(HttpStatusCode.NotFound, otherCompany.StatusCode);
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task SemBancoDoPortalConfiguradoAGravacaoRespondeQueNaoEstaDisponivel()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = new AuthPortal(backend, "Portal:UseDemoData", "true");
        using HttpClient client = await LoggedInAsync(portal);

        using var response = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", Terms());
        using var list = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.NotImplemented, response.StatusCode);
        Assert.Equal("client-decisions-not-available", (string?)(await response.Content.ReadFromJsonAsync<JsonObject>())!["code"]);
        Assert.Equal(HttpStatusCode.OK, list.StatusCode);
    }

    [Fact]
    public async Task OsCamposComMascaraSaoGravadosSemPontosNemTracos()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var created = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", new
        {
            billing = new
            {
                company = "EMPRESA", cnpj = "12.abc.345/01de-35", stateRegistration = "123.456.789-0", street = "Rua 1",
                city = "Curitiba", state = "pr", postalCode = "80.000-000", phone = "(41) 99999-9999",
                invoiceEmails = InvoiceEmails
            },
            approval = new { requesterName = "Marina", responsibleName = "Joao", jobTitle = "Eng", date = "2026-11-15", allowsResultsByEmail = false }
        });

        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        BillingAddress billing = Assert.Single(store.Recorded).Terms!.Billing;
        Assert.Equal(("12ABC34501DE35", "1234567890", "PR", "80000000", "41999999999"),
            (billing.Cnpj, billing.StateRegistration, billing.State, billing.PostalCode, billing.Phone));
    }

    [Theory]
    [InlineData("cnpj", "1234567800019")]
    [InlineData("cnpj", "12345678000AB1")]
    [InlineData("postalCode", "8000-000")]
    [InlineData("state", "P1")]
    [InlineData("phone", "41 9999")]
    public async Task CampoComFormatoErradoERecusado(string field, string value)
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);
        Dictionary<string, object> billing = new()
        {
            ["company"] = "EMPRESA", ["cnpj"] = "12345678000190", ["street"] = "Rua 1", ["city"] = "Curitiba",
            ["state"] = "PR", ["postalCode"] = "80000000", ["phone"] = "4133334444", ["invoiceEmails"] = InvoiceEmails
        };
        billing[field] = value;

        using var response = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", new
        {
            billing,
            approval = new { requesterName = "Marina", responsibleName = "Joao", jobTitle = "Eng", date = "2026-11-15", allowsResultsByEmail = false }
        });

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task EmailMaiorQueOLimiteDoProtocoloERecusado()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);
        string longEmail = new string('a', 64) + "@" + new string('b', 186) + ".com";

        using var response = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", new
        {
            billing = new
            {
                company = "EMPRESA", cnpj = "30960572000183", street = "Rua 1", city = "Curitiba", state = "PR",
                postalCode = "80000-000", invoiceEmails = InvoiceEmails
            },
            approval = new { requesterName = "Marina", responsibleName = "Joao", jobTitle = "Eng", date = "2026-11-15", allowsResultsByEmail = true, resultsEmail = longEmail }
        });

        Assert.Equal(255, longEmail.Length);
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.EndsWith(": approval.resultsEmail.", await DetailOfAsync(response));
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task OsEmailsDeNfseSaoGravadosComoListaSemEspacosENaOrdemInformada()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var created = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance",
            TermsWithInvoiceEmails(" fiscal@empresa.com ", "financeiro@empresa.com", "contabil@escritorio.com.br"));

        Assert.Equal(HttpStatusCode.Created, created.StatusCode);
        Assert.Equal(
            ["fiscal@empresa.com", "financeiro@empresa.com", "contabil@escritorio.com.br"],
            Assert.Single(store.Recorded).Terms!.Billing.InvoiceEmails);
    }

    [Theory]
    [InlineData("billing.invoiceEmails.")]
    [InlineData("billing.invoiceEmails.", "financeiro@empresa.com")]
    [InlineData("billing.invoiceEmails.", "a@empresa.com", "b@empresa.com", "c@empresa.com", "d@empresa.com", "e@empresa.com", "f@empresa.com")]
    [InlineData("billing.invoiceEmails[1].", "financeiro@empresa.com", "Financeiro@Empresa.com")]
    [InlineData("billing.invoiceEmails[1].", "financeiro@empresa.com", "sem-arroba")]
    [InlineData("billing.invoiceEmails[1].", "financeiro@empresa.com", "")]
    [InlineData("billing.invoiceEmails[0].", "Fiscal <fiscal@empresa.com>", "financeiro@empresa.com")]
    [InlineData("billing.invoiceEmails[0].", "fiscal@empresa.com; outro@empresa.com", "financeiro@empresa.com")]
    [InlineData("billing.invoiceEmails[2].", "fiscal@empresa.com", "financeiro@empresa.com", "joão@empresa.com")]
    public async Task ListaDeEmailsDeNfseForaDaRegraERecusadaApontandoOCampo(string problem, params string[] emails)
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);

        using var response = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", TermsWithInvoiceEmails(emails));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.EndsWith(": " + problem, await DetailOfAsync(response));
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task EmailDeNfseMaiorQueOLimiteDoProtocoloERecusado()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);
        string longEmail = new string('a', 64) + "@" + new string('b', 186) + ".com";

        using var response = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance",
            TermsWithInvoiceEmails("financeiro@empresa.com", longEmail));

        Assert.Equal(255, longEmail.Length);
        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.EndsWith(": billing.invoiceEmails[1].", await DetailOfAsync(response));
        Assert.Empty(store.Recorded);
    }

    [Fact]
    public async Task EmailsDeNfseNumTextoSoNaoSaoInterpretadosPeloServidor()
    {
        InMemoryClientDecisionRepository store = new();
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity());
        using var portal = PortalWith(backend, store);
        using HttpClient client = await LoggedInAsync(portal);
        JsonObject terms = JsonSerializer.SerializeToNode(Terms())!.AsObject();
        terms["billing"]!["invoiceEmails"] = "financeiro@empresa.com, fiscal@empresa.com";

        using var response = await client.PostAsJsonAsync("/api/quotes/quote-3/acceptance", terms);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Empty(store.Recorded);
    }

    private static Microsoft.AspNetCore.Mvc.Testing.WebApplicationFactory<Program> PortalWith(
        AuthHandler backend, InMemoryClientDecisionRepository store) =>
        new AuthPortal(backend, "Portal:UseDemoData", "true").WithWebHostBuilder(builder =>
            builder.ConfigureTestServices(services => services.AddSingleton<IClientDecisionRepository>(store)));

    private static async Task<HttpClient> LoggedInAsync(Microsoft.AspNetCore.Mvc.Testing.WebApplicationFactory<Program> portal)
    {
        HttpClient client = portal.CreateClient();
        using var response = await client.PostAsJsonAsync("/auth/login", new { email = "marina@empresa.com", password = "senha" });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        JsonObject body = (await response.Content.ReadFromJsonAsync<JsonObject>())!;
        client.DefaultRequestHeaders.Authorization = new("Bearer", (string)body["Token"]!);
        return client;
    }

    private static MultipartFormDataContent FileForm(string fileName, byte[] content)
    {
        ByteArrayContent file = new(content);
        file.Headers.ContentType = new MediaTypeHeaderValue("application/pdf");
        return new MultipartFormDataContent { { file, "file", fileName } };
    }

    private static readonly string[] InvoiceEmails = ["financeiro@empresa.com", "fiscal@empresa.com"];

    private static JsonObject TermsWithInvoiceEmails(params string[] emails)
    {
        JsonObject terms = JsonSerializer.SerializeToNode(Terms())!.AsObject();
        terms["billing"]!["invoiceEmails"] = new JsonArray([.. emails.Select(email => (JsonNode?)JsonValue.Create(email))]);
        return terms;
    }

    private static async Task<string> DetailOfAsync(HttpResponseMessage response) =>
        (string)(await response.Content.ReadFromJsonAsync<JsonObject>())!["detail"]!;

    private static object Terms() => new
    {
        billing = new
        {
            company = "EMPRESA DE ENERGIA", cnpj = "30960572000183", stateRegistration = "", street = "Rua das Flores, 10",
            city = "Curitiba", state = "PR", district = "Centro", postalCode = "80000000", contact = "Marina",
            phone = "41999999999", invoiceEmails = InvoiceEmails
        },
        approval = new
        {
            requesterName = "Marina Costa", responsibleName = "Joao Lima", jobTitle = "Engenheira",
            department = "Manutencao", date = "2026-11-15", allowsResultsByEmail = true, resultsEmail = "laudos@empresa.com"
        }
    };

    private static string Identity() => JsonSerializer.Serialize(new
    {
        id = "42", name = "Marina Costa", email = "marina@empresa.com", role = "CLIENTE",
        serviceCode = "portal-web", authorizations = new { companyCnpjs = new[] { FakeSalesforceQuoteRepository.EnergyCnpj } }
    });
}
