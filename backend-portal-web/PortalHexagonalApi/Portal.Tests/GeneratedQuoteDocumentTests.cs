using Portal.Adapters.Quotes;
using Portal.Application.Quotes;
using Portal.Tests.Quotes;

using Xunit;

namespace Portal.Tests;

public sealed class GeneratedQuoteDocumentTests
{
    [Fact]
    public async Task WrittenProposalAnswersBeforeTheGeneratedOne()
    {
        byte[] written = "%PDF-1.4 proposta escrita a mao"u8.ToArray();
        var documents = new GeneratedQuoteDocumentRepository(
            new FakeSalesforceQuoteRepository(),
            new FixedDocumentRepository(written));

        byte[]? document = await documents.GetDocumentAsync("quote-3", CancellationToken.None);

        Assert.Equal(written, document);
    }

    [Fact]
    public async Task QuoteWithoutWrittenProposalGetsOneWithItsOwnData()
    {
        var documents = new GeneratedQuoteDocumentRepository(
            new FakeSalesforceQuoteRepository(),
            new FixedDocumentRepository(null));

        byte[]? document = await documents.GetDocumentAsync("quote-15", CancellationToken.None);

        Assert.NotNull(document);
        string text = PdfText.Collapsed(PdfText.OfWholeDocument(document));
        Assert.Contains("EAQ_2026_45496_V_1", text);
        Assert.Contains("DISTRIBUIDORADEENERGIA", text);
        Assert.Contains("R$11.270,00", text);
    }

    /// <summary>
    /// O gerador antigo montava o PDF a mao e a suite conferia a tabela de referencia cruzada
    /// dele, porque um deslocamento errado faz o leitor recusar o arquivo. A conferencia saiu
    /// com o gerador: a tabela agora e escrita pela QuestPDF, e testa-la seria testar a
    /// biblioteca. O que prova que o arquivo abre e o leitor da suite conseguir ler as cinco
    /// paginas dele.
    /// </summary>
    [Fact]
    public async Task GeneratedProposalOpensWithFivePages()
    {
        var documents = new GeneratedQuoteDocumentRepository(
            new FakeSalesforceQuoteRepository(),
            new FixedDocumentRepository(null));

        byte[] document = (await documents.GetDocumentAsync("quote-15", CancellationToken.None))!;

        Assert.Equal(5, PdfText.PageCountOf(document));
    }

    [Fact]
    public async Task UnknownQuoteHasNoDocumentToGenerate()
    {
        var documents = new GeneratedQuoteDocumentRepository(
            new FakeSalesforceQuoteRepository(),
            new FixedDocumentRepository(null));

        byte[]? document = await documents.GetDocumentAsync("quote-ausente", CancellationToken.None);

        Assert.Null(document);
    }

    private sealed class FixedDocumentRepository(byte[]? document) : IQuoteDocumentRepository
    {
        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken) =>
            Task.FromResult(document);
    }
}
