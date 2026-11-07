using Portal.Adapters.Quotes;
using Portal.Application.Quotes;
using Portal.Tests.Pipeline;

using Xunit;

namespace Portal.Tests.Quotes;

/// <summary>
/// A aplicacao pela porta condutora, com duble na porta conduzida do documento. O que estes
/// testes provam nao e o corpo HTTP: e que a aplicacao nem chega a perguntar o arquivo
/// enquanto nao houve proposta escrita. Um duble que contasse so o que respondeu nao veria a
/// diferenca, porque o cliente recebe 404 das duas formas, e um adaptador que vai a rede
/// pagaria uma ida por clique de botao.
/// </summary>
public sealed class QuoteDocumentAvailabilityTests
{
    [Theory]
    [InlineData("quote-1")]
    [InlineData("quote-4")]
    [InlineData("quote-5")]
    public async Task OrcamentoAntesDaNegociacaoNaoChegaAPerguntarODocumento(string quoteId)
    {
        CountingDocuments documents = new();
        QuoteService service = ServiceWith(documents);

        byte[]? document = await service.GetDocumentAsync(quoteId, CancellationToken.None);

        Assert.Null(document);
        Assert.Equal(0, documents.Calls);
    }

    [Theory]
    [InlineData("quote-3")]
    [InlineData("quote-6")]
    [InlineData("quote-15")]
    public async Task OrcamentoDaNegociacaoEmDiantePerguntaODocumento(string quoteId)
    {
        CountingDocuments documents = new();
        QuoteService service = ServiceWith(documents);

        byte[]? document = await service.GetDocumentAsync(quoteId, CancellationToken.None);

        Assert.NotNull(document);
        Assert.Equal(1, documents.Calls);
    }

    [Theory]
    [InlineData("quote-16")]
    [InlineData("quote-17")]
    public async Task OrcamentoEncerradoContinuaComODocumentoQueOClienteRecusou(string quoteId)
    {
        CountingDocuments documents = new();
        QuoteService service = ServiceWith(documents);

        byte[]? document = await service.GetDocumentAsync(quoteId, CancellationToken.None);

        Assert.NotNull(document);
        Assert.Equal(1, documents.Calls);
    }

    [Fact]
    public async Task OrcamentoInexistenteNaoChegaAPerguntarODocumento()
    {
        CountingDocuments documents = new();
        QuoteService service = ServiceWith(documents);

        byte[]? document = await service.GetDocumentAsync("quote-ausente", CancellationToken.None);

        Assert.Null(document);
        Assert.Equal(0, documents.Calls);
    }

    private static QuoteService ServiceWith(IQuoteDocumentRepository documents) =>
        new(new FakeSalesforceQuoteRepository(), documents, new InMemoryLaboratoryProgressRepository());

    private sealed class CountingDocuments : IQuoteDocumentRepository
    {
        public int Calls { get; private set; }

        public Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
        {
            Calls++;
            return Task.FromResult<byte[]?>("%PDF-1.4 duble"u8.ToArray());
        }
    }
}
