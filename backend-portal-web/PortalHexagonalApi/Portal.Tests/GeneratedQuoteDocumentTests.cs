using System.Text;

using Portal.Adapters.Quotes;
using Portal.Application.Quotes;

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
        string text = Encoding.Latin1.GetString(document);
        Assert.StartsWith("%PDF-1.4", text);
        Assert.Contains("EAQ_2026_45496_V_1", text);
        Assert.Contains("Distribuidora Exemplo Ltda", text);
        Assert.Contains("R$ 11.270,00", text);
    }

    /// <summary>
    /// A tabela de referencia cruzada guarda deslocamento em bytes, e leitor que a confere
    /// recusa o arquivo quando o numero nao aponta para o lugar certo. Verde na suite sem
    /// esta conferencia significaria so que a string foi montada, nao que o PDF abre.
    /// </summary>
    [Fact]
    public async Task GeneratedProposalPointsItsCrossReferenceTableAtTheRightByte()
    {
        var documents = new GeneratedQuoteDocumentRepository(
            new FakeSalesforceQuoteRepository(),
            new FixedDocumentRepository(null));

        byte[] document = (await documents.GetDocumentAsync("quote-15", CancellationToken.None))!;

        string text = Encoding.Latin1.GetString(document);
        int declared = int.Parse(text.Split("startxref\n")[1].Split('\n')[0]);
        Assert.Equal("xref", Encoding.Latin1.GetString(document, declared, 4));
        Assert.EndsWith("%%EOF\n", text);
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
