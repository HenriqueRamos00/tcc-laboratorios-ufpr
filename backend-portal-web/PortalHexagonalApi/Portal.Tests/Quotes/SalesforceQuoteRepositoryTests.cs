using System.Net;
using System.Text;
using Microsoft.AspNetCore.WebUtilities;
using Portal.Adapters.Quotes;
using Portal.Adapters.Salesforce;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Xunit;

namespace Portal.Tests.Quotes;

public sealed class SalesforceQuoteRepositoryTests
{
    [Fact]
    public async Task ListaFiltraPeloCnpjDaEmpresaDoCliente()
    {
        using var handler = new SalesforceQueryHandler();
        using var http = new HttpClient(handler);
        var tokens = new QuoteTokenProvider();
        var repository = Create(http, tokens, "76436849000174");

        await repository.GetQuotesAsync(CancellationToken.None);

        string query = Assert.Single(handler.Queries);
        Assert.Contains("Opportunity.Account.CNPJ__c", query);
        Assert.EndsWith("FROM Quote WHERE Opportunity.Account.CNPJ__c = '76436849000174' ORDER BY CreatedDate DESC LIMIT 5", query);
    }

    [Fact]
    public async Task MultiplasEmpresasUsamInSemDuplicatas()
    {
        using var handler = new SalesforceQueryHandler();
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174", "12ABC34501DE35", "76436849000174");

        await repository.GetQuotesAsync(CancellationToken.None);

        Assert.Contains("WHERE Opportunity.Account.CNPJ__c IN ('12ABC34501DE35', '76436849000174') ORDER BY", Assert.Single(handler.Queries));
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

        Assert.Empty(await repository.GetQuotesAsync(CancellationToken.None));
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

        Assert.Empty(await repository.GetQuotesAsync(CancellationToken.None));
        Assert.Null(await repository.GetQuoteByIdAsync("quote-3", CancellationToken.None));
        Assert.Empty(handler.Queries);
        Assert.Equal(0, tokens.Calls);
    }

    [Fact]
    public async Task RenovacaoDoTokenSalesforcePreservaOFiltro()
    {
        using var handler = new SalesforceQueryHandler { FirstResponseUnauthorized = true };
        using var http = new HttpClient(handler);
        var tokens = new QuoteTokenProvider();
        var repository = Create(http, tokens, "76436849000174");

        await repository.GetQuotesAsync(CancellationToken.None);

        Assert.Equal(1, tokens.RefreshCalls);
        Assert.Equal(2, handler.Queries.Count);
        Assert.Equal(handler.Queries[0], handler.Queries[1]);
        Assert.Contains("WHERE Opportunity.Account.CNPJ__c = '76436849000174'", handler.Queries[1]);
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

        var failure = await Assert.ThrowsAsync<QuoteSourceUnavailableException>(() => repository.GetQuotesAsync(CancellationToken.None));

        Assert.DoesNotContain("sensitive-upstream-details", failure.ToString());
    }

    [Fact]
    public async Task TimeoutRetornaFalhaDaOrigem()
    {
        using var handler = new SalesforceQueryHandler { Failure = new TaskCanceledException() };
        using var http = new HttpClient(handler);
        var repository = Create(http, new QuoteTokenProvider(), "76436849000174");

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(() => repository.GetQuotesAsync(CancellationToken.None));
    }

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

internal sealed class SalesforceQueryHandler : HttpMessageHandler
{
    public List<string> Queries { get; } = [];
    public HttpStatusCode Status { get; init; } = HttpStatusCode.OK;
    public string Body { get; init; } = "{\"records\":[]}";
    public bool FirstResponseUnauthorized { get; init; }
    public Exception? Failure { get; init; }
    protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        Assert.Equal("Bearer", request.Headers.Authorization?.Scheme);
        Assert.Equal("/services/data/v60.0/query/", request.RequestUri!.AbsolutePath);
        Queries.Add(QueryHelpers.ParseQuery(request.RequestUri.Query)["q"].ToString());
        if (Failure is not null) throw Failure;
        return Task.FromResult(new HttpResponseMessage(FirstResponseUnauthorized && Queries.Count == 1 ? HttpStatusCode.Unauthorized : Status)
        { Content = new StringContent(Body, Encoding.UTF8, "application/json") });
    }
}
