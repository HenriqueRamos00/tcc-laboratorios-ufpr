using Portal.Application.Quotes;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

/// <summary>
/// Entrega a proposta comercial de um orcamento enquanto o documento do Salesforce
/// nao esta integrado: a proposta escrita a mao responde primeiro, e o orcamento que
/// nao tem arquivo recebe uma pagina montada com os dados dele mesmo.
///
/// O nome diz de onde vem o documento, e nao com quem ele fala, porque e isso que
/// muda quando o adaptador do Salesforce entrar no lugar deste.
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
        return quote is null ? null : QuoteProposalPdf.Write(quote);
    }
}
