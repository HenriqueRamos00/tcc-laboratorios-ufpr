using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Nodes;

using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;

using Portal.Application.Pipeline;
using Portal.Application.Pipeline.Exceptions;
using Portal.Application.Quotes;
using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;
using Portal.Tests.Authentication;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// O contrato, capturado do corpo HTTP real da aplicacao de verdade com o adaptador de
/// verdade. Serializar um objeto num teste prova o conversor; subir o host prova o
/// pipeline, que e onde o camelCase e o tratamento de nulo sao decididos.
///
/// A afirmacao que mais importa aqui e a de compatibilidade: a mudanca e aditiva, e os doze
/// campos que a tela ja lia saem com o mesmo nome e o mesmo valor.
/// </summary>
public sealed class QuoteStageContractTests
{
    private const string LaboratoryQuoteCode = "EAQ_2026_45494_V_1";
    private const string CommercialQuoteCode = "EAQ_2026_45482_V_1";

    [Fact]
    public async Task TheQuoteBodyKeepsEveryFieldTheScreenAlreadyReads()
    {
        using var portal = PortalWithQuotes("in-memory-sample");
        using HttpClient client = portal.CreateClient();
        await Login(client);

        JsonNode quote = await FirstQuote(client);

        Assert.Equal("quote-lab", (string?)quote["id"]);
        Assert.Equal("opportunity-lab", (string?)quote["opportunityId"]);
        Assert.Equal(LaboratoryQuoteCode, (string?)quote["code"]);
        Assert.Equal("Pacote de ensaios", (string?)quote["name"]);
        Assert.Equal("Em Andamento", (string?)quote["status"]);
        Assert.Equal("Finalizado", (string?)quote["stage"]);
        Assert.Equal("Orcamento de teste", (string?)quote["description"]);
        Assert.Equal("EMPRESA DE ENERGIA", (string?)quote["companyName"]);
        Assert.Equal("Equipe Comercial", (string?)quote["externalContactName"]);
        Assert.Equal("comercial@exemplo.com", (string?)quote["externalContactEmail"]);
        Assert.Equal(1000m, (decimal?)quote["totalPrice"]);
        Assert.NotNull((string?)quote["createdDate"]);
    }

    [Fact]
    public async Task TheQuoteBodyCarriesTheLaboratoryStageAsTheKeyTheScreenUses()
    {
        using var portal = PortalWithQuotes("in-memory-sample");
        using HttpClient client = portal.CreateClient();
        await Login(client);

        JsonNode quote = await FirstQuote(client);

        Assert.Equal("elaborando-relatorio", (string?)quote["laboratoryStage"]);
    }

    /// <summary>
    /// O campo viaja nulo em vez de ser omitido. Campo omitido a tela nao consegue
    /// distinguir de campo que ela nao conhece, e o conversor da aplicacao declara
    /// JsonIgnoreCondition.Never justamente por isso.
    /// </summary>
    [Fact]
    public async Task AQuoteOutsideTheLaboratoryCarriesTheFieldAsNull()
    {
        using var portal = PortalWithQuotes("in-memory-sample");
        using HttpClient client = portal.CreateClient();
        await Login(client);

        JsonArray quotes = await Quotes(client);
        JsonNode commercial = quotes.Single(quote => (string?)quote!["code"] == CommercialQuoteCode)!;

        Assert.True(commercial.AsObject().ContainsKey("laboratoryStage"));
        Assert.Null((string?)commercial["laboratoryStage"]);
    }

    [Fact]
    public async Task WithoutTheSourceConfiguredNoQuoteCarriesALaboratoryStage()
    {
        using var portal = PortalWithQuotes(source: null);
        using HttpClient client = portal.CreateClient();
        await Login(client);

        JsonArray quotes = await Quotes(client);

        Assert.All(quotes, quote => Assert.Null((string?)quote!["laboratoryStage"]));
    }

    [Fact]
    public async Task TheDetailOfOneQuoteCarriesTheSameLaboratoryStage()
    {
        using var portal = PortalWithQuotes("in-memory-sample");
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var response = await client.GetAsync("/api/quotes/quote-lab");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        JsonNode quote = JsonNode.Parse(await response.Content.ReadAsStringAsync())!;
        Assert.Equal("elaborando-relatorio", (string?)quote["laboratoryStage"]);
    }

    /// <summary>
    /// A queda da fonte de etapas diz que foi ela que caiu, e nao a fonte de orcamentos:
    /// quem atende precisa saber em qual sistema olhar. A mensagem de dentro nao vai para o
    /// fio, porque vem da infraestrutura.
    /// </summary>
    [Fact]
    public async Task AFailureOfTheLaboratorySourceAnswers502NamingThatSource()
    {
        using var portal = PortalWithQuotes("not-connected", new FailingLaboratory());
        using HttpClient client = portal.CreateClient();
        await Login(client);

        using var response = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        string body = await response.Content.ReadAsStringAsync();
        Assert.Equal("laboratory-progress-source-unavailable", (string?)JsonNode.Parse(body)!["code"]);
        Assert.DoesNotContain("servidor interno fora do ar", body, StringComparison.Ordinal);
    }

    private static WebApplicationFactory<Program> PortalWithQuotes(
        string? source,
        ILaboratoryProgressRepository? laboratory = null)
    {
        // A chave viaja sempre, e a cadeia vazia e o que diz "fonte nao configurada". Omitir a
        // chave deixaria o appsettings de desenvolvimento responder pelo teste, e foi o que
        // aconteceu: o caso da fonte ausente passou a ler "in-memory-sample" do arquivo.
        AuthPortal portal = new(
            new AuthHandler(HttpStatusCode.OK, Identity()),
            "LaboratoryProgress:Source",
            source ?? string.Empty);

        return portal.WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
        {
            services.AddScoped<IQuoteRepository>(_ => new ScriptedQuoteRepository());
            services.AddScoped<IQuoteDocumentRepository>(_ => new ScriptedDocuments());

            if (laboratory is not null)
                services.AddScoped(_ => laboratory);
        }));
    }

    private static async Task<JsonArray> Quotes(HttpClient client)
    {
        using var response = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);

        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsArray();
    }

    private static async Task<JsonNode> FirstQuote(HttpClient client)
    {
        JsonArray quotes = await Quotes(client);

        return quotes.Single(quote => (string?)quote!["code"] == LaboratoryQuoteCode)!;
    }

    private static string Identity() => JsonSerializer.Serialize(new
    {
        id = "42",
        name = "Cliente",
        email = "client@example.test",
        role = "CLIENTE",
        serviceCode = "portal-web",
        authorizations = new { companyCnpjs = new[] { "76436849000174" } }
    });

    private static async Task Login(HttpClient client)
    {
        using var response = await client.PostAsJsonAsync(
            "/auth/login",
            new { email = "client@example.test", password = "original" });

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        client.DefaultRequestHeaders.Authorization = new(
            "Bearer",
            (string?)JsonNode.Parse(await response.Content.ReadAsStringAsync())!["Token"]);
    }

    private sealed class ScriptedQuoteRepository : IQuoteRepository
    {
        private static readonly Quote[] Quotes =
        [
            QuoteOf("quote-lab", LaboratoryQuoteCode),
            QuoteOf("quote-commercial", CommercialQuoteCode)
        ];

        public Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
        {
            return Task.FromResult<IReadOnlyList<Quote>>([.. Quotes]);
        }

        public Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
        {
            return Task.FromResult(Quotes.FirstOrDefault(quote => quote.Id == id));
        }

        private static Quote QuoteOf(string id, string code)
        {
            return new Quote(
                id: id,
                opportunityId: id == "quote-lab" ? "opportunity-lab" : "opportunity-commercial",
                code: code,
                name: "Pacote de ensaios",
                status: "Em Andamento",
                stage: "Finalizado",
                description: "Orcamento de teste",
                companyName: "EMPRESA DE ENERGIA",
                externalContactName: "Equipe Comercial",
                externalContactEmail: "comercial@exemplo.com",
                totalPrice: 1000m,
                createdDate: new DateTimeOffset(2026, 5, 5, 8, 30, 0, TimeSpan.Zero));
        }
    }

    private sealed class ScriptedDocuments : IQuoteDocumentRepository
    {
        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
        {
            return Task.FromResult<byte[]?>("%PDF-1.4 teste"u8.ToArray());
        }
    }

    private sealed class FailingLaboratory : ILaboratoryProgressRepository
    {
        public Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
            IReadOnlyCollection<string> quoteCodes,
            CancellationToken cancellationToken)
        {
            throw new LaboratoryProgressSourceUnavailableException("servidor interno fora do ar");
        }

        public Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
            string quoteCode,
            CancellationToken cancellationToken)
        {
            throw new LaboratoryProgressSourceUnavailableException("servidor interno fora do ar");
        }
    }
}
