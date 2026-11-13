using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Http;
using Portal.Adapters.Quotes;
using Portal.Api.Controllers;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Tests.Pipeline;

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

        AssertProblem(result, StatusCodes.Status404NotFound);
        Assert.Equal(0, documents.Calls);
    }

    [Fact]
    public async Task DocumentEndpointReturnsNotFoundWhenFixtureIsMissing()
    {
        string directory = Path.Combine(Path.GetTempPath(), Guid.NewGuid().ToString("N"));
        var controller = CreateController(new LocalQuoteDocumentRepository(directory));

        var result = await controller.GetDocument("quote-3", CancellationToken.None);

        AssertProblem(result, StatusCodes.Status404NotFound);
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

    /// <summary>
    /// O adaptador le o arquivo do diretorio que recebe, byte a byte.
    ///
    /// O caso escreve a propria massa em vez de ler um documento entregue junto com a
    /// aplicacao. Havia um PDF de demonstracao versionado em Documents, e ele respondia antes
    /// do gerador: o unico orcamento que tinha arquivo era o unico que nunca mostrava a
    /// proposta montada com os dados do orcamento. O arquivo saiu, e o teste do adaptador
    /// deixou de depender dele.
    /// </summary>
    [Fact]
    public async Task LocalAdapterReadsTheDocumentItFindsOnDisk()
    {
        string directory = Directory.CreateTempSubdirectory().FullName;
        byte[] written = System.Text.Encoding.ASCII.GetBytes("%PDF-1.4 documento de teste");

        try
        {
            await File.WriteAllBytesAsync(Path.Combine(directory, "quote-3.pdf"), written);

            byte[]? document = await new LocalQuoteDocumentRepository(directory)
                .GetDocumentAsync("quote-3", CancellationToken.None);

            Assert.Equal(written, document);
        }
        finally
        {
            Directory.Delete(directory, recursive: true);
        }
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

    private static QuotesController CreateController(IQuoteDocumentRepository documents) =>
        new(new QuoteService(
            new FakeSalesforceQuoteRepository(), documents, new InMemoryLaboratoryProgressRepository()));

    private sealed class FixedDocumentRepository(byte[] document) : IQuoteDocumentRepository
    {
        public int Calls { get; private set; }

        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult<byte[]?>(document);
        }
    }

    /// <summary>
    /// Confere o contrato, e nao a classe de resultado do MVC: o que o cliente ve e o status
    /// e o corpo de problema, e trocar <c>NotFound</c> por <c>StatusCode</c> nao muda nenhum
    /// dos dois.
    /// </summary>
    private static void AssertProblem(IActionResult result, int expectedStatus)
    {
        ObjectResult response = Assert.IsType<ObjectResult>(result);
        ProblemDetails problem = Assert.IsType<ProblemDetails>(response.Value);

        Assert.Equal(expectedStatus, response.StatusCode);
        Assert.Equal(expectedStatus, problem.Status);
        Assert.False(string.IsNullOrWhiteSpace((string?)problem.Extensions["code"]));
    }
}
