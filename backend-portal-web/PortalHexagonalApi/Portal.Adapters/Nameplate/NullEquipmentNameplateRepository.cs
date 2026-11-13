using Portal.Application.Nameplate;
using Portal.Domain.Nameplate;

namespace Portal.Adapters.Nameplate;

/// <summary>
/// Ator conduzido que responde sempre que o cadastro nao sabe nada. E o Null Object do padrao
/// de mesmo nome, e fica com o nome conhecido porque qualquer outro esconderia o que ele e.
///
/// Ele nao e um adaptador, e sim um ator que nao precisa de adaptador: nao ha tecnologia do
/// outro lado dele, e batiza-lo por uma tecnologia ausente prometeria fidelidade que ele nao
/// tem.
///
/// Existe porque o portal tem um deployment em que o cadastro nao esta conectado, e nesse
/// deployment a resposta honesta nao e um erro nem um dado inventado: e a ausencia de noticia,
/// que faz a tela dizer "Nao informado" em tipo, tensao e potencia, exatamente como antes
/// desta fatia.
/// </summary>
public sealed class NullEquipmentNameplateRepository : IEquipmentNameplateRepository
{
    public Task<IReadOnlyList<EquipmentNameplate>> GetNameplatesOfAsync(
        IReadOnlyCollection<string> serialNumbers,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<IReadOnlyList<EquipmentNameplate>>([]);
    }

    public Task<EquipmentNameplate?> GetNameplateOfAsync(
        string serialNumber,
        CancellationToken cancellationToken)
    {
        return Task.FromResult<EquipmentNameplate?>(null);
    }
}
