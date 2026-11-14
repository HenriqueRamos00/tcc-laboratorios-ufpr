using Portal.Application.Paging;
using Portal.Domain.Quotes;

namespace Portal.Application.Quotes;

/// <summary>
/// Porta condutora dos orcamentos. A etapa do laboratorio viaja ao lado do orcamento, e
/// nao dentro dele: <c>Quote</c> e o que a fonte comercial disse, e tres adaptadores
/// diferentes a montam. Acrescentar um campo de laboratorio na entidade obrigaria o
/// adaptador do Salesforce, o do backend-lab e o de demonstracao a declarar uma etapa que
/// nenhum dos tres conhece.
/// </summary>
public interface IQuoteUseCase
{
    Task<Page<QuoteWithLaboratoryStage>> GetQuotesAsync(PageRequest page, CancellationToken cancellationToken);
    Task<QuoteWithLaboratoryStage?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken);
    Task<byte[]?> GetDocumentAsync(string id, CancellationToken cancellationToken);
}
