namespace Portal.Application.Quotes;

public interface IQuoteDocumentRepository
{
    Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken);
}
