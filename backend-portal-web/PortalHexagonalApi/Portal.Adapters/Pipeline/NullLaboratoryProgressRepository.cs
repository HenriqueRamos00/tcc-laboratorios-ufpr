using Portal.Application.Pipeline;
using Portal.Domain.Pipeline;

namespace Portal.Adapters.Pipeline;

/// <summary>
/// Ator conduzido que responde sempre que o laboratorio nao tem nada a dizer. E o Null
/// Object do padrao de mesmo nome, e fica com o nome conhecido porque qualquer outro
/// esconderia o que ele e.
///
/// Ele nao e um adaptador, e sim um ator que nao precisa de adaptador: nao ha tecnologia do
/// outro lado dele, e batiza-lo por uma tecnologia ausente prometeria fidelidade que ele
/// nao tem.
///
/// Existe porque o portal tem um deployment em que o AutoLAB nao esta conectado, e nesse
/// deployment a resposta honesta nao e um erro nem um dado inventado: e a ausencia de
/// noticia, que faz a tela mostrar a etapa que o lado comercial escreveu, exatamente como
/// antes desta fatia.
/// </summary>
public sealed class NullLaboratoryProgressRepository : ILaboratoryProgressRepository
{
    public Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<IReadOnlyList<LaboratoryProgress>>([]);
    }

    public Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
        string quoteCode,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<LaboratoryProgress?>(null);
    }
}
