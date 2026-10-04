using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Http;
using Portal.Adapters.Quotes;
using Portal.Api.Controllers;
using Portal.Application.Quotes;
using Xunit;

namespace Portal.Tests;

public sealed class QuoteDocumentTests
{
    [Fact]
    public async Task QuoteFixtureHasRequestedCodeStatusAndTotal()
    {
        var quotes = new FakeSalesforceQuoteRepository();

        var quote = await quotes.GetQuoteByIdAsync("quote-3", CancellationToken.None);

        Assert.NotNull(quote);
        Assert.Equal("EAQ_2026_45484_V_1", quote.Code);
        Assert.Equal("Pendente de Aceite", quote.Status);
        Assert.Equal(3450.00m, quote.TotalPrice);
    }

    [Fact]
    public async Task DocumentEndpointReturnsRawPdfBytesAndContentType()
    {
        byte[] pdf = "%PDF-1.4 fixture"u8.ToArray();
        var controller = CreateController(new FixedDocumentRepository(pdf));

        var result = await controller.GetDocument("quote-3", CancellationToken.None);

        var file = Assert.IsType<FileContentResult>(result);
        Assert.Equal("application/pdf", file.ContentType);
        Assert.Equal(pdf, file.FileContents);
    }

    [Fact]
    public async Task DocumentEndpointReturnsNotFoundForUnknownQuote()
    {
        var documents = new FixedDocumentRepository([]);
        var controller = CreateController(documents);

        var result = await controller.GetDocument("missing", CancellationToken.None);

        Assert.IsType<NotFoundObjectResult>(result);
        Assert.Equal(0, documents.Calls);
    }

    [Fact]
    public async Task DocumentEndpointReturnsNotFoundWhenFixtureIsMissing()
    {
        string directory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        var controller = CreateController(new LocalQuoteDocumentRepository(directory));

        var result = await controller.GetDocument("quote-3", CancellationToken.None);

        Assert.IsType<NotFoundObjectResult>(result);
    }

    [Fact]
    public async Task DocumentEndpointMapsDependencyFailureToBadGateway()
    {
        string directory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(Path.Combine(directory, "quote-3.pdf"));
        var controller = CreateController(new LocalQuoteDocumentRepository(directory));

        try
        {
            var result = await controller.GetDocument("quote-3", CancellationToken.None);

            var response = Assert.IsType<ObjectResult>(result);
            Assert.Equal(StatusCodes.Status502BadGateway, response.StatusCode);
        }
        finally
        {
            Directory.Delete(directory, recursive: true);
        }
    }

    [Fact]
    public async Task LocalAdapterReturnsNullWhenFixtureIsMissing()
    {
        string directory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        var adapter = new LocalQuoteDocumentRepository(directory);

        var document = await adapter.GetDocumentAsync("quote-3", CancellationToken.None);

        Assert.Null(document);
    }

    [Fact]
    public async Task LocalAdapterLoadsTheShippedPdfFixture()
    {
        var adapter = new LocalQuoteDocumentRepository();

        byte[]? document = await adapter.GetDocumentAsync("quote-3", CancellationToken.None);

        Assert.NotNull(document);
        Assert.StartsWith("%PDF-1.4", System.Text.Encoding.ASCII.GetString(document));
        Assert.Contains("TOTAL DA PROPOSTA", System.Text.Encoding.ASCII.GetString(document));
        Assert.Contains("R$ 3.450,00", System.Text.Encoding.ASCII.GetString(document));
    }

    [Fact]
    public async Task LocalAdapterPropagatesCancellation()
    {
        string directory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        var adapter = new LocalQuoteDocumentRepository(directory);
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            adapter.GetDocumentAsync("quote-3", cancellation.Token));
    }

    [Fact]
    public async Task ThirdListedQuoteHasMatchingPdfDocument()
    {
        var quotes = await new FakeSalesforceQuoteRepository().GetQuotesAsync(CancellationToken.None);
        var thirdQuote = quotes[2];
        Assert.Equal("quote-2", thirdQuote.Id);
        var controller = CreateController(new LocalQuoteDocumentRepository());

        var result = Assert.IsType<FileContentResult>(await controller.GetDocument(thirdQuote.Id!, CancellationToken.None));

        Assert.Equal("application/pdf", result.ContentType);
        var text = System.Text.Encoding.ASCII.GetString(result.FileContents);
        Assert.StartsWith("%PDF-1.4", text);
        Assert.Contains(thirdQuote.Code!, text);
        Assert.Contains("Empresa Exemplo Beta S.A.", text);
        Assert.Contains("R$ 12.500,00", text);
    }

    private static QuotesController CreateController(IQuoteDocumentRepository documents) =>
        new(new QuoteService(new FakeSalesforceQuoteRepository(), documents));

    private sealed class FixedDocumentRepository(byte[] document) : IQuoteDocumentRepository
    {
        public int Calls { get; private set; }

        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult<byte[]?>(document);
        }
    }
}
