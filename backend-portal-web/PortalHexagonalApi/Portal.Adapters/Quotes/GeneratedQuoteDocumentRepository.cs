using Portal.Application.Quotes;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

/// <summary>
/// Entrega a proposta comercial de um orcamento enquanto o documento do Salesforce
/// nao esta integrado: a proposta escrita a mao responde primeiro, e o orcamento que
/// nao tem arquivo recebe um documento montado com os dados dele mesmo.
///
/// O nome diz de onde vem o documento, e nao com quem ele fala, porque e isso que
/// muda quando o adaptador do Salesforce entrar no lugar deste.
///
/// A lista de itens vai vazia porque nenhuma fonte de hoje a produz: a consulta do
/// Salesforce nao pede <c>QuoteLineItem</c> e o contrato do Spring nao traz itens. O
/// documento desenha a secao 2 com a tabela, o cabecalho e o valor total mesmo assim, e
/// diz na propria tabela que a relacao nao e publicada; a alternativa seria somar itens
/// que ninguem informou.
/// </summary>
public sealed class GeneratedQuoteDocumentRepository : IQuoteDocumentRepository
{
    private readonly IQuoteRepository _quotes;
    private readonly IQuoteDocumentRepository _writtenDocuments;

    public GeneratedQuoteDocumentRepository(
        IQuoteRepository quotes,
        IQuoteDocumentRepository writtenDocuments)
    {
        _quotes = quotes;
        _writtenDocuments = writtenDocuments;
    }

    public async Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
    {
        byte[]? written = await _writtenDocuments.GetDocumentAsync(quoteId, cancellationToken);
        if (written is not null) return written;

        Quote? quote = await _quotes.GetQuoteByIdAsync(quoteId, cancellationToken);
        return quote is null ? null : QuotationPdf.Write(quote, []);
    }
}
