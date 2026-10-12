using Portal.Application.LabResults;
using Portal.Domain.LabResults;

namespace Portal.Tests.LabResults;

/// <summary>
/// Adaptador conduzido de teste, e o nome diz a tecnologia que ele de fato usa: memoria.
/// Existe para que a suite alcance a aplicacao pela porta condutora, sem subir servidor e
/// sem passar pelo adaptador de producao.
///
/// Ele conta quais perguntas recebeu, e nao so o que respondeu. A porta oferece tres
/// perguntas diferentes de proposito, e qual delas o servico escolhe e uma decisao de
/// desenho: perguntar o inquilino inteiro e filtrar na memoria devolve a mesma resposta e
/// obriga um adaptador de banco a trazer todos os laudos do cliente para descartar quase
/// tudo. Sem o contador, essa troca passa com a suite verde, e ja passou.
/// </summary>
public sealed class InMemoryLabTestRepository : ILabTestRepository
{
    private readonly List<SampleAnalysis> _analyses;

    public InMemoryLabTestRepository(params SampleAnalysis[] analyses)
    {
        _analyses = [.. analyses];
    }

    public int TimesTheWholeTenantWasAsked { get; private set; }

    public int TimesOneEquipmentWasAsked { get; private set; }

    public int TimesOneProtocolWasAsked { get; private set; }

    public Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfTenantAsync(
        string tenant,
        CancellationToken cancellationToken)
    {
        TimesTheWholeTenantWasAsked++;

        IReadOnlyList<SampleAnalysis> found = [.. OfTenant(tenant)];

        return Task.FromResult(found);
    }

    public Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        string tenant,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        TimesOneEquipmentWasAsked++;

        IReadOnlyList<SampleAnalysis> found = [.. OfTenant(tenant)
            .Where(analysis => string.Equals(analysis.EquipmentKey, equipmentKey, StringComparison.Ordinal))];

        return Task.FromResult(found);
    }

    /// <summary>
    /// O adaptador embutido de producao carrega a amostra inteira, entao a analise pedida
    /// aqui sempre ja estava na serie e esta operacao nunca chega a responder nada. Com um
    /// adaptador paginado ela passa a ser o unico caminho, e e por isso que o dublê a
    /// implementa de verdade em vez de devolver nulo.
    /// </summary>
    public Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        string tenant,
        string protocoloLactec,
        CancellationToken cancellationToken)
    {
        TimesOneProtocolWasAsked++;

        SampleAnalysis? found = OfTenant(tenant)
            .FirstOrDefault(analysis => string.Equals(
                analysis.ProtocoloLactec, protocoloLactec, StringComparison.Ordinal));

        return Task.FromResult(found);
    }

    private IEnumerable<SampleAnalysis> OfTenant(string tenant)
    {
        return _analyses.Where(analysis =>
            string.Equals(analysis.Company, tenant.Trim(), StringComparison.OrdinalIgnoreCase));
    }
}
