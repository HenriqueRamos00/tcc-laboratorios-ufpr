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
using Portal.Application.Quotes;
using Portal.Tests.Authentication;
using Xunit;

namespace Portal.Tests.Quotes;

public sealed class CompanyQuoteContractTests
{
    private const string QuoteResponse = """
        {"records":[{"Id":"quote-3","Name":"Proposta autorizada","Status":"Draft","OpportunityId":"opp-1",
        "Opportunity":{"Account":{"Name":"Empresa autorizada","CNPJ__c":"76436849000174"}}}]}
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
        string query = Assert.Single(salesforce.Queries);
        Assert.Contains(multiple ? "CNPJ__c IN ('12ABC34501DE35', '76436849000174')" : "CNPJ__c = '76436849000174'", query);
        Assert.DoesNotContain("99999999999999", query);
        Assert.Equal(1, backend.Count);
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

    private static WebApplicationFactory<Program> WithSalesforce(AuthPortal portal, SalesforceQueryHandler handler,
        QuoteTokenProvider tokens, Documents documents) => portal.WithWebHostBuilder(builder =>
        builder.ConfigureTestServices(services =>
        {
            services.AddSingleton<ISalesforceTokenProvider>(tokens);
            services.AddSingleton<IQuoteDocumentRepository>(documents);
            services.AddHttpClient<IQuoteRepository, SalesforceQuoteRepository>().ConfigurePrimaryHttpMessageHandler(() => handler);
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
