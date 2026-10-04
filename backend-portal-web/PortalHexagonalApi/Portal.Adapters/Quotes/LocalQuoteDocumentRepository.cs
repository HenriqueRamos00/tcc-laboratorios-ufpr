using Portal.Application.Common;
using Portal.Application.Quotes;

namespace Portal.Adapters.Quotes;

// Test adapter: only explicit fixtures have local commercial documents.
public sealed class LocalQuoteDocumentRepository : IQuoteDocumentRepository
{
    private readonly string _documentDirectory;

    public LocalQuoteDocumentRepository(string? documentDirectory = null)
    {
        _documentDirectory = string.IsNullOrWhiteSpace(documentDirectory)
            ? Path.Combine(AppContext.BaseDirectory, "Documents")
            : documentDirectory;
    }

    public async Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
    {
        if (quoteId is not ("quote-2" or "quote-3")) return null;

        try
        {
            return await File.ReadAllBytesAsync(Path.Combine(_documentDirectory, $"{quoteId}.pdf"), cancellationToken);
        }
        catch (FileNotFoundException) { return null; }
        catch (DirectoryNotFoundException) { return null; }
        catch (IOException) { throw new ExternalServiceException("Could not read the quote document."); }
        catch (UnauthorizedAccessException) { throw new ExternalServiceException("Could not read the quote document."); }
    }
}
