using Portal.Application.Paging;
using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

// Quote includes the Opportunity data required by the portal.
public interface IQuoteRepository
{
    /// <summary>
    /// Uma pagina dos orcamentos que o cliente alcanca, com o total de todos eles. A pagina
    /// e pedida a fonte, e nao recortada aqui: o Salesforce aplica <c>LIMIT</c>/<c>OFFSET</c>
    /// e conta o resto, em vez de entregar tudo para a aplicacao descartar.
    /// </summary>
    Task<Page<Quote>> GetQuotesAsync(PageRequest page, CancellationToken cancellationToken);
    Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken);
}
