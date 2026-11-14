using System.Net;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.AspNetCore.WebUtilities;
using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
using Portal.Application.Paging;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;
using Xunit;

namespace Portal.Tests.Quotes;

public sealed class SalesforceQuoteRepositoryTests
{
    private const string Filter = "Opportunity.Account.CNPJ__c = '76436849000174'";
    private const string Ordered = "ORDER BY CreatedDate DESC, Id DESC";

    [Fact]
    public async Task ListaContaEPaginaComOFiltroPeloCnpjDaEmpresaDoCliente()
    {
        using var handler = new SalesforceQueryHandler { Body = Records(3), TotalSize = 120 };
        using var http = new HttpClient(handler);
        var tokens = new QuoteTokenProvider();
        var repository = Create(http, tokens, "76436849000174");

        Page<Quote> page = await repository.GetQuotesAsync(new PageRequest(1, 50), CancellationToken.None);

        Assert.Equal(2, handler.Queries.Count);
        Assert.Equal($"SELECT COUNT() FROM Quote WHERE {Filter}", handler.Queries[0]);
        Assert.EndsWith($"FROM Quote WHERE {Filter} {Ordered} LIMIT 50", handler.Queries[1]);
        Assert.DoesNotContain("OFFSET", handler.Queries[1]);
        Assert.Equal(120, page.TotalCount);
        Assert.Equal(3, page.Items.Count);
        Assert.True(page.HasNext);
    }

    [Theory]
    [InlineData(2, 50, "LIMIT 50 OFFSET 50")]
    [InlineData(3, 7, "LIMIT 7 OFFSET 14")]
    [InlineData(11, 200, "LIMIT 200 OFFSET 2000")]
    public async Task PaginaVaiParaOSalesforceComoLimitEOffset(int number, int size, string expected)
    {
        using var handler = new SalesforceQueryHandler { Body = Records(1), TotalSize = 5000 };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        Page<Quote> page = await repository.GetQuotesAsync(new PageRequest(number, size), CancellationToken.None);

        Assert.EndsWith($"WHERE {Filter} {Ordered} {expected}", handler.Queries[^1]);
        Assert.Equal(number, page.PageNumber);
        Assert.Equal(size, page.PageSize);
        Assert.Equal(5000, page.TotalCount);
    }

    [Fact]
    public async Task PaginaAlemDoTotalNaoFazASegundaConsulta()
    {
        using var handler = new SalesforceQueryHandler { Body = Records(2), TotalSize = 2 };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        Page<Quote> page = await repository.GetQuotesAsync(new PageRequest(2, 50), CancellationToken.None);

        Assert.Equal($"SELECT COUNT() FROM Quote WHERE {Filter}", Assert.Single(handler.Queries));
        Assert.Empty(page.Items);
        Assert.Equal(2, page.TotalCount);
    }

    /// <summary>
    /// O SOQL recusa OFFSET acima de 2000. A pagina seguinte nao vira 502: a consulta e
    /// limitada ao fim da pagina e o cursor do Salesforce e seguido ate ela.
    /// </summary>
    [Fact]
    public async Task PaginaDepoisDoOffsetMaximoSegueOCursorDoSalesforce()
    {
        using var handler = new SalesforceCursorHandler(total: 2600, batch: 1000);
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        Page<Quote> page = await repository.GetQuotesAsync(new PageRequest(22, 100), CancellationToken.None);

        Assert.Equal(2, handler.Queries.Count);
        Assert.Equal($"SELECT COUNT() FROM Quote WHERE {Filter}", handler.Queries[0]);
        Assert.EndsWith($"WHERE {Filter} {Ordered} LIMIT 2200", handler.Queries[1]);
        Assert.DoesNotContain("OFFSET", handler.Queries[1]);
        Assert.Equal(["/services/data/v60.0/query/cursor-1000", "/services/data/v60.0/query/cursor-2000"], handler.CursorPaths);
        Assert.Equal(2600, page.TotalCount);
        Assert.Equal(Enumerable.Range(2100, 100).Select(index => $"quote-{index}"), page.Items.Select(quote => quote.Id));
    }

    [Theory]
    [InlineData("https://outro-host.test/services/data/v60.0/query/cursor-1000")]
    [InlineData("/outra-api/cursor-1000")]
    public async Task CursorForaDaApiDeConsultaNaoRecebeOToken(string nextRecordsUrl)
    {
        using var handler = new SalesforceCursorHandler(total: 2600, batch: 1000) { NextRecordsUrl = nextRecordsUrl };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetQuotesAsync(new PageRequest(22, 100), CancellationToken.None));
        Assert.Empty(handler.CursorPaths);
    }

    [Fact]
    public async Task MultiplasEmpresasUsamInSemDuplicatasNasDuasConsultas()
    {
        using var handler = new SalesforceQueryHandler { Body = Records(1) };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174", "12ABC34501DE35", "76436849000174");

        await repository.GetQuotesAsync(PageRequest.First, CancellationToken.None);

        Assert.Equal(2, handler.Queries.Count);
        Assert.All(handler.Queries, query =>
            Assert.Contains("WHERE Opportunity.Account.CNPJ__c IN ('12ABC34501DE35', '76436849000174')", query));
    }

    [Fact]
    public async Task ConsultaPorIdCombinaOIdentificadorEscapadoComOEscopo()
    {
        using var handler = new SalesforceQueryHandler();
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        await repository.GetQuoteByIdAsync("quote'id", CancellationToken.None);

        Assert.EndsWith("WHERE Id = 'quote\\'id' AND Opportunity.Account.CNPJ__c = '76436849000174' LIMIT 1", Assert.Single(handler.Queries));
    }

    [Fact]
    public async Task CodigoDaCotacaoEPedidoEMapeadoSemSubstituirOCodigoPublico()
    {
        using var handler = new SalesforceQueryHandler
        {
            Body = """
                {"records":[{"Id":"quote-1","Codigo_da_Cotacao__c":"EAQ_2024_37569_V_1",
                "Opportunity":{"Codigo_da_Oportunidade__c":"EAQ_2099_99999_V_1"}}]}
                """
        };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        Quote? quote = await repository.GetQuoteByIdAsync("quote-1", CancellationToken.None);

        Assert.Contains("Codigo_da_Cotacao__c", Assert.Single(handler.Queries));
        Assert.Equal("EAQ_2099_99999_V_1", quote!.Code);
        Assert.Equal("EAQ_2024_37569_V_1", quote.QuotationCode);
        Assert.Equal("2024_37569", quote.LaboratoryKey?.Value);
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData(" ")]
    [InlineData("76436849000174' OR Id != ''")]
    [InlineData("76436849000174\n")]
    [InlineData("76.436.849/0001-74")]
    public async Task EscopoInvalidoNaoConsultaSalesforce(string? invalidCnpj)
    {
        using var handler = new SalesforceQueryHandler();
        using var http = new HttpClient(handler);
        var tokens = new QuoteTokenProvider();
        var repository = Create(http, tokens, "04252011000110", invalidCnpj!);

        Page<Quote> page = await repository.GetQuotesAsync(PageRequest.First, CancellationToken.None);
        Assert.Empty(page.Items);
        Assert.Equal(0, page.TotalCount);
        Assert.Null(await repository.GetQuoteByIdAsync("quote-3", CancellationToken.None));
        Assert.Empty(handler.Queries);
        Assert.Equal(0, tokens.Calls);
    }

    [Fact]
    public async Task SemEmpresasNaoConsultaSalesforce()
    {
        using var handler = new SalesforceQueryHandler();
        using var http = new HttpClient(handler);
        var tokens = new QuoteTokenProvider();
        var repository = Create(http, tokens);

        Assert.Empty((await repository.GetQuotesAsync(PageRequest.First, CancellationToken.None)).Items);
        Assert.Null(await repository.GetQuoteByIdAsync("quote-3", CancellationToken.None));
        Assert.Empty(handler.Queries);
        Assert.Equal(0, tokens.Calls);
    }

    [Fact]
    public async Task RenovacaoDoTokenSalesforcePreservaOFiltro()
    {
        using var handler = new SalesforceQueryHandler { FirstResponseUnauthorized = true, Body = Records(1) };
        using var http = new HttpClient(handler);
        var tokens = new QuoteTokenProvider();
        var repository = Create(http, tokens, "76436849000174");

        await repository.GetQuotesAsync(PageRequest.First, CancellationToken.None);

        Assert.Equal(1, tokens.RefreshCalls);
        Assert.Equal(3, handler.Queries.Count);
        Assert.Equal(handler.Queries[0], handler.Queries[1]);
        Assert.All(handler.Queries, query => Assert.Contains($"WHERE {Filter}", query));
    }

    [Theory]
    [InlineData(400, "sensitive-upstream-details")]
    [InlineData(503, "sensitive-upstream-details")]
    [InlineData(200, "{")]
    [InlineData(200, "null")]
    public async Task RespostaInvalidaDaOrigemNaoVazaDetalhes(int status, string body)
    {
        using var handler = new SalesforceQueryHandler { Status = (HttpStatusCode)status, Body = body };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        var failure = await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetQuotesAsync(PageRequest.First, CancellationToken.None));

        Assert.DoesNotContain("sensitive-upstream-details", failure.ToString());
    }

    [Fact]
    public async Task TimeoutRetornaFalhaDaOrigem()
    {
        using var handler = new SalesforceQueryHandler { Failure = new TaskCanceledException() };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetQuotesAsync(PageRequest.First, CancellationToken.None));
    }

    private static string Records(int count) => JsonSerializer.Serialize(new
    {
        records = Enumerable.Range(1, count).Select(index => new { Id = $"quote-{index}" })
    });

    private static SalesforceQuoteRepository Create(HttpClient http, ISalesforceTokenProvider tokens, params string[] cnpjs) =>
        new(http, tokens, new SalesforceOptions(), new FixedScope(cnpjs));

    private sealed class FixedScope(string[] cnpjs) : IQuoteCompanyScope
    {
        public IReadOnlyList<string> CompanyCnpjs => cnpjs;
    }
}

internal sealed class QuoteTokenProvider : ISalesforceTokenProvider
{
    public int Calls { get; private set; }
    public int RefreshCalls { get; private set; }
    public Task<SalesforceToken> GetTokenAsync(CancellationToken cancellationToken)
    {
        Calls++;
        return Task.FromResult(new SalesforceToken("salesforce-token", "Bearer", "https://salesforce.test/", null, null, null));
    }
    public Task<SalesforceToken> RefreshTokenAsync(CancellationToken cancellationToken)
    {
        RefreshCalls++;
        return GetTokenAsync(cancellationToken);
    }
}

/// <summary>
/// Salesforce simulado. <c>SELECT COUNT()</c> responde <c>totalSize</c>, que vem de
/// <see cref="TotalSize"/> ou, sem ele, da quantidade de registros de <see cref="Body"/>; as
/// demais consultas respondem <see cref="Body"/>.
/// </summary>
internal sealed class SalesforceQueryHandler : HttpMessageHandler
{
    public List<string> Queries { get; } = [];
    public HttpStatusCode Status { get; init; } = HttpStatusCode.OK;
    public string Body { get; init; } = "{\"records\":[]}";
    public int? TotalSize { get; init; }
    public bool FirstResponseUnauthorized { get; init; }
    public Exception? Failure { get; init; }
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Assert.Equal("Bearer", request.Headers.Authorization?.Scheme);
        Assert.Equal("/services/data/v60.0/query/", request.RequestUri!.AbsolutePath);
        string query = QueryHelpers.ParseQuery(request.RequestUri.Query)["q"].ToString();
        Queries.Add(query);
        if (Failure is not null) throw Failure;
        string body = query.StartsWith("SELECT COUNT() ", StringComparison.Ordinal) ? CountBody() : Body;
        return Task.FromResult(new HttpResponseMessage(FirstResponseUnauthorized && Queries.Count == 1 ? HttpStatusCode.Unauthorized : Status)
        { Content = new StringContent(body, Encoding.UTF8, "application/json") });
    }

    private string CountBody()
    {
        int? records;
        try
        {
            records = JsonNode.Parse(Body)?["records"]?.AsArray().Count;
        }
        catch (Exception failure) when (failure is JsonException or InvalidOperationException)
        {
            records = null;
        }

        // Corpo invalido continua invalido na contagem, para os testes de falha da origem.
        int? total = TotalSize ?? records;
        return total is null ? Body : $"{{\"totalSize\":{total},\"done\":true,\"records\":[]}}";
    }
}

/// <summary>
/// Salesforce com cursor: a consulta limitada responde o primeiro lote com
/// <c>nextRecordsUrl</c>, e cada caminho de cursor responde o lote seguinte. Os registros
/// sao numerados pela posicao na ordem da consulta.
/// </summary>
internal sealed class SalesforceCursorHandler(int total, int batch) : HttpMessageHandler
{
    public List<string> Queries { get; } = [];
    public List<string> CursorPaths { get; } = [];
    public string? NextRecordsUrl { get; init; }
    private int _limit;

    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Assert.Equal("Bearer", request.Headers.Authorization?.Scheme);
        Assert.Equal("salesforce.test", request.RequestUri!.Host);
        string path = request.RequestUri.AbsolutePath;
        if (path == "/services/data/v60.0/query/")
        {
            string query = QueryHelpers.ParseQuery(request.RequestUri.Query)["q"].ToString();
            Queries.Add(query);
            if (query.StartsWith("SELECT COUNT() ", StringComparison.Ordinal))
                return Respond(new { totalSize = total, done = true, records = Array.Empty<object>() });
            int limitAt = query.LastIndexOf(" LIMIT ", StringComparison.Ordinal);
            _limit = Math.Min(total, int.Parse(query[(limitAt + 7)..], System.Globalization.CultureInfo.InvariantCulture));
            return Batch(0);
        }

        CursorPaths.Add(path);
        const string prefix = "/services/data/v60.0/query/cursor-";
        Assert.StartsWith(prefix, path);
        return Batch(int.Parse(path[prefix.Length..], System.Globalization.CultureInfo.InvariantCulture));
    }

    private Task<HttpResponseMessage> Batch(int start)
    {
        int end = Math.Min(start + batch, _limit);
        bool done = end >= _limit;
        return Respond(new
        {
            totalSize = _limit,
            done,
            nextRecordsUrl = done ? null : NextRecordsUrl ?? $"/services/data/v60.0/query/cursor-{end}",
            records = Enumerable.Range(start, end - start).Select(index => new { Id = $"quote-{index}" })
        });
    }

    private static Task<HttpResponseMessage> Respond(object body) =>
        Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json")
        });
}
