using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
using Portal.Application.Authentication;
using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Application.ReportDocuments;
using Portal.Application.Visibility;
using Portal.Tests.Authentication;
using Xunit;

namespace Portal.Tests.Quotes;

public sealed class CompanyQuoteContractTests
{
    // A etapa entrou na massa porque a rota do documento passou a depender dela: antes da
    // negociacao nao ha proposta escrita e o download responde 404. Sem a etapa, o registro
    // cai em qualificacao e estes testes de escopo por empresa mediriam a regra errada.
    private const string QuoteResponse = """
        {"records":[{"Id":"quote-3","Name":"Proposta autorizada","Status":"Draft","OpportunityId":"opp-1",
        "Opportunity":{"StageName":"Negociação","Account":{"Name":"Empresa autorizada","CNPJ__c":"76436849000174"}}}]}
        """;

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task LoginEJwtLimitamAListaAsEmpresasDoCliente(bool multiple)
    {
        string[] cnpjs = multiple ? ["76436849000174", "12ABC34501DE35"] : ["76436849000174"];
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(cnpjs));
        using var salesforce = new SalesforceQueryHandler { Body = QuoteResponse };
        var tokens = new QuoteTokenProvider();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, tokens, new Documents());
        using HttpClient client = portal.CreateClient();
        await Login(client);

        // A requested CNPJ cannot broaden the companies in the signed JWT.
        using var response = await client.GetAsync("/api/quotes?companyCnpj=99999999999999");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        JsonArray quotes = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
        Assert.Single(quotes);
        Assert.Equal("Proposta autorizada", (string?)quotes[0]!["name"]);
        Assert.Equal(2, salesforce.Queries.Count);
        Assert.StartsWith("SELECT COUNT() FROM Quote", salesforce.Queries[0]);
        Assert.All(salesforce.Queries, query =>
        {
            Assert.Contains(multiple ? "CNPJ__c IN ('12ABC34501DE35', '76436849000174')" : "CNPJ__c = '76436849000174'", query);
            Assert.DoesNotContain("99999999999999", query);
        });
        Assert.Equal("1", Assert.Single(response.Headers.GetValues("X-Total-Count")));
        Assert.Equal(1, backend.Count);
    }

    [Fact]
    public async Task ListaPaginaNoSalesforceEPublicaOTotalNosCabecalhos()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(["76436849000174"]));
        using var salesforce = new SalesforceQueryHandler { Body = QuoteResponse, TotalSize = 73 };
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, new QuoteTokenProvider(), new Documents());
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var response = await client.GetAsync("/api/quotes?page=3&pageSize=20");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("73", Assert.Single(response.Headers.GetValues("X-Total-Count")));
        Assert.Equal("3", Assert.Single(response.Headers.GetValues("X-Page")));
        Assert.Equal("20", Assert.Single(response.Headers.GetValues("X-Page-Size")));
        Assert.Single(JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray());
        Assert.EndsWith("WHERE Opportunity.Account.CNPJ__c = '76436849000174' ORDER BY CreatedDate DESC, Id DESC LIMIT 20 OFFSET 40",
            salesforce.Queries[^1]);
    }

    [Theory]
    [InlineData("page=0")]
    [InlineData("pageSize=0")]
    [InlineData("pageSize=201")]
    [InlineData("page=abc")]
    public async Task PaginaInvalidaResponde400SemConsultarSalesforce(string query)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(["76436849000174"]));
        using var salesforce = new SalesforceQueryHandler { Body = QuoteResponse };
        var tokens = new QuoteTokenProvider();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, tokens, new Documents());
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var response = await client.GetAsync($"/api/quotes?{query}");

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Empty(salesforce.Queries);
        Assert.Equal(0, tokens.Calls);
    }

    [Fact]
    public async Task ClienteSemEmpresasRecebeListaVaziaE404NoDetalheEDocumento()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity([]));
        using var salesforce = new SalesforceQueryHandler();
        var tokens = new QuoteTokenProvider();
        var documents = new Documents();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, tokens, documents);
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var list = await client.GetAsync("/api/quotes");
        Assert.Equal(HttpStatusCode.OK, list.StatusCode);
        Assert.Empty(JsonNode.Parse(await list.Content.ReadAsStringAsync())!.AsArray());
        foreach (string path in new[] { "/api/quotes/quote-3", "/api/quotes/quote-3/document" })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        }
        Assert.Equal(0, tokens.Calls);
        Assert.Empty(salesforce.Queries);
        Assert.Equal(0, documents.Calls);
    }

    [Theory]
    [InlineData(false)]
    [InlineData(true)]
    public async Task DetalheEDocumentoRespeitamOMesmoFiltro(bool allowed)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(["76436849000174"]));
        using var salesforce = new SalesforceQueryHandler { Body = allowed ? QuoteResponse : "{\"records\":[]}" };
        var documents = new Documents();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, new QuoteTokenProvider(), documents);
        using HttpClient client = portal.CreateClient();
        await Login(client);

        foreach (string path in new[] { "/api/quotes/quote-3", "/api/quotes/quote-3/document" })
        {
            using var response = await client.GetAsync(path);
            Assert.Equal(allowed ? HttpStatusCode.OK : HttpStatusCode.NotFound, response.StatusCode);
        }
        Assert.Equal(allowed ? 1 : 0, documents.Calls);
        Assert.Equal(2, salesforce.Queries.Count);
        Assert.All(salesforce.Queries, query => Assert.EndsWith("WHERE Id = 'quote-3' AND Opportunity.Account.CNPJ__c = '76436849000174' LIMIT 1", query));
    }

    [Fact]
    public async Task RequisicoesDeClientesDiferentesNaoCompartilhamOEscopo()
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity([]));
        using var salesforce = new SalesforceQueryHandler();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, new QuoteTokenProvider(), new Documents());
        using HttpClient client = portal.CreateClient();
        var issuer = portal.Services.GetRequiredService<IPortalSessionIssuer>();
        foreach (string cnpj in new[] { "76436849000174", "04252011000110" })
        {
            ClientSession session = issuer.Issue(new ClientUser("client-" + cnpj, "Cliente", "client@example.test", "CLIENTE", null,
                "portal-web", new ClientAuthorizations([cnpj])));
            client.DefaultRequestHeaders.Authorization = new("Bearer", session.AccessToken);
            using var response = await client.GetAsync("/api/quotes");
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        }
        Assert.Equal(2, salesforce.Queries.Count);
        Assert.Contains("CNPJ__c = '76436849000174'", salesforce.Queries[0]);
        Assert.DoesNotContain("04252011000110", salesforce.Queries[0]);
        Assert.Contains("CNPJ__c = '04252011000110'", salesforce.Queries[1]);
        Assert.DoesNotContain("76436849000174", salesforce.Queries[1]);
    }

    [Theory]
    [InlineData("/api/quotes")]
    [InlineData("/api/quotes/quote-3")]
    [InlineData("/api/quotes/quote-3/document")]
    public async Task FalhaDoSalesforceRetorna502SemDadosDeDemonstracao(string path)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(["76436849000174"]));
        using var salesforce = new SalesforceQueryHandler { Status = HttpStatusCode.BadRequest, Body = "sensitive-upstream-details" };
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, new QuoteTokenProvider(), new Documents());
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var response = await client.GetAsync(path);
        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        string body = await response.Content.ReadAsStringAsync();
        Assert.Equal("quote-source-unavailable", (string?)JsonNode.Parse(body)!["code"]);
        Assert.DoesNotContain("sensitive-upstream-details", body);
    }

    /// <summary>
    /// Relatorios por orcamento ligados pelo codigo da cotacao (Codigo_da_Cotacao__c), na
    /// forma nua que o AutoLAB guarda, e nunca pelo codigo da oportunidade, que aqui e outro
    /// numero de proposito. Codigo ausente ou fora do formato responde 204 sem consultar o
    /// AutoLAB; parametros da URL nao trocam a chave.
    /// </summary>
    [Theory]
    [InlineData("EAQ_2024_37569_V_1", "2024_37569")]
    [InlineData("EAQ_2024_37569_V_12", "2024_37569")]
    [InlineData("EAQ_2024_37569", "2024_37569")]
    [InlineData("2024_37569", "2024_37569")]
    [InlineData(" EAQ_2024_37569_V_1 ", "2024_37569")]
    [InlineData(null, null)]
    [InlineData("", null)]
    [InlineData("EAQ_2024_37569_V_", null)]
    [InlineData("EAQ_2024_37569_V_1_extra", null)]
    [InlineData("EAQ_2024_37569_UHAB", null)]
    [InlineData("EAQ_2024_37569' OR 1=1", null)]
    public async Task RelatoriosDoOrcamentoUsamOCodigoDaCotacaoAutorizado(string? quotationCode, string? expected)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(["76436849000174"]));
        using var salesforce = new SalesforceQueryHandler { Body = QuotationJson(quotationCode) };
        var reports = new LaboratoryReports();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, new QuoteTokenProvider(), new Documents(), reports);
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var response = await client.GetAsync("/api/quotes/quote-1/reports?quoteCode=EAQ_2099_99999_V_1&OrcamentoCRM=2099_99999");

        string query = Assert.Single(salesforce.Queries);
        Assert.Contains("Codigo_da_Cotacao__c", query);
        Assert.EndsWith("WHERE Id = 'quote-1' AND Opportunity.Account.CNPJ__c = '76436849000174' LIMIT 1", query);
        if (expected is null)
        {
            Assert.Equal(HttpStatusCode.NoContent, response.StatusCode);
            Assert.Empty(reports.Codes);
            Assert.Equal("0", Assert.Single(response.Headers.GetValues("X-Total-Count")));
            return;
        }

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(expected, Assert.Single(reports.Codes));
        JsonObject conclusion = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal("quote-1", (string?)conclusion["quoteId"]);
        Assert.Equal("EAQ_2099_99999_V_1", (string?)conclusion["quoteCode"]);
        JsonNode report = Assert.Single(conclusion["reports"]!.AsArray())!;
        Assert.Equal(expected, (string?)report["quoteCode"]);
        Assert.Equal("Publicado", (string?)report["status"]);
        Assert.Equal("LACTEC-00001/2024", (string?)report["identification"]);
    }

    [Theory]
    [InlineData("sem-jwt", HttpStatusCode.Unauthorized)]
    [InlineData("sem-empresas", HttpStatusCode.NotFound)]
    [InlineData("fora-do-escopo", HttpStatusCode.NotFound)]
    public async Task OrcamentoInacessivelNuncaConsultaOAutoLab(string caso, HttpStatusCode expected)
    {
        using var backend = new AuthHandler(HttpStatusCode.OK, Identity(caso == "sem-empresas" ? [] : ["76436849000174"]));
        using var salesforce = new SalesforceQueryHandler
        {
            Body = caso == "fora-do-escopo" ? "{\"records\":[]}" : QuotationJson("EAQ_2024_37569_V_1")
        };
        var reports = new LaboratoryReports();
        using var basePortal = new AuthPortal(backend);
        using var portal = WithSalesforce(basePortal, salesforce, new QuoteTokenProvider(), new Documents(), reports);
        using HttpClient client = portal.CreateClient();
        if (caso != "sem-jwt") await Login(client);

        using var response = await client.GetAsync("/api/quotes/quote-1/reports");

        Assert.Equal(expected, response.StatusCode);
        Assert.Empty(reports.Codes);
        Assert.Equal(caso == "fora-do-escopo" ? 1 : 0, salesforce.Queries.Count);
    }

    private static string QuotationJson(string? quotationCode) => JsonSerializer.Serialize(new
    {
        records = new[]
        {
            new
            {
                Id = "quote-1",
                Codigo_da_Cotacao__c = quotationCode,
                Opportunity = new { Codigo_da_Oportunidade__c = "EAQ_2099_99999_V_1", StageName = "Fechado ganho" }
            }
        }
    });

    private static WebApplicationFactory<Program> WithSalesforce(AuthPortal portal, SalesforceQueryHandler handler,
        QuoteTokenProvider tokens, Documents documents, LaboratoryReports? reports = null) => portal.WithWebHostBuilder(builder =>
        builder.ConfigureTestServices(services =>
        {
            services.AddSingleton<ISalesforceTokenProvider>(tokens);
            services.AddSingleton<IQuoteDocumentRepository>(documents);
            services.AddHttpClient<IQuoteRepository, SalesforceQuoteRepository>().ConfigurePrimaryHttpMessageHandler(() => handler);
            if (reports is not null) services.AddSingleton<IReportIssueRepository>(reports);
        }));

    private static string Identity(string[] cnpjs) => JsonSerializer.Serialize(new
    {
        id = "42", name = "Cliente", email = "client@example.test", role = "CLIENTE", serviceCode = "portal-web",
        authorizations = new { companyCnpjs = cnpjs }
    });

    private static async Task Login(HttpClient client)
    {
        using var response = await client.PostAsJsonAsync("/auth/login", new { email = "client@example.test", password = "original" });
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        client.DefaultRequestHeaders.Authorization = new("Bearer", (string?)JsonNode.Parse(await response.Content.ReadAsStringAsync())!["Token"]);
    }

    /// <summary>Duble do AutoLAB que conta por quais chaves foi perguntado.</summary>
    private sealed class LaboratoryReports : IReportIssueRepository
    {
        public List<string?> Codes { get; } = [];

        public Task<Page<LabReport>> ListReportsAsync(
            VisibilityScope scope, string? quoteCode, PageRequest page, CancellationToken cancellationToken)
        {
            Codes.Add(quoteCode);
            LabReport report = new(Guid.Parse("19d6b0d0-c90d-4c10-80b7-702ff9f5839e"), "LACTEC-00001/2024", "SOL-2024-00001",
                quoteCode, "AM-00001/2024-001", "Óleo isolante", "EMPRESA DE ENERGIA", "Publicado", null, null, null, false);
            return Task.FromResult(Page<LabReport>.Of([report], page));
        }

        public Task<SignedReportDocument?> GetSignedDocumentOfAsync(string reportIdentification, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");

        public Task<IReadOnlySet<string>> GetIdentificationsWithSignedDocumentAsync(
            IReadOnlyCollection<string> reportIdentifications, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");

        public Task<ReportIssue?> GetCurrentIssueOfAsync(string reportIdentification, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");

        public Task<SignedReportDocument?> GetSignedDocumentOfReportAsync(
            VisibilityScope scope, Guid reportId, CancellationToken cancellationToken) =>
            throw new InvalidOperationException("Fora do escopo deste teste.");
    }

    private sealed class Documents : IQuoteDocumentRepository
    {
        public int Calls { get; private set; }
        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult<byte[]?>("%PDF-1.4 authorized"u8.ToArray());
        }
    }
}
