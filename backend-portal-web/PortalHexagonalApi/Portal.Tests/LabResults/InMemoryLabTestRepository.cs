using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Tests.LabResults;

/// <summary>
/// Adaptador conduzido de teste, e o nome diz a tecnologia que ele de fato usa: memoria.
/// Existe para que a suite alcance a aplicacao pela porta condutora, sem subir servidor e
/// sem passar pelo adaptador de producao.
///
/// Ele conta quais perguntas recebeu, e nao so o que respondeu. A porta oferece tres
/// perguntas diferentes de proposito, e qual delas o servico escolhe e uma decisao de
/// desenho: perguntar o escopo inteiro e filtrar na memoria devolve a mesma resposta e
/// obriga um adaptador de banco a trazer todos os laudos para descartar quase tudo. Sem o
/// contador, essa troca passa com a suite verde, e ja passou.
/// </summary>
public sealed class InMemoryLabTestRepository : ILabTestRepository
{
    private readonly List<SampleAnalysis> _analyses;

    public InMemoryLabTestRepository(params SampleAnalysis[] analyses)
    {
        _analyses = [.. analyses];
    }

    public int TimesTheWholeTenantWasAsked { get; private set; }

    public int TimesOneProtocolWasAsked { get; private set; }

    public int TimesAPageOfReportsWasAsked { get; private set; }

    public List<PageRequest> PagesAsked { get; } = [];

    public Task<Page<SampleAnalysis>> GetAnalysesAsync(
        VisibilityScope scope,
        PageRequest equipmentPage,
        CancellationToken cancellationToken)
    {
        TimesTheWholeTenantWasAsked++;
        PagesAsked.Add(equipmentPage);

        List<SampleAnalysis> ofTenant = [.. In(scope)];

        Page<string> keys = Page<string>.Of(
            TestedEquipment.Gather(ofTenant).Select(equipment => equipment.EquipmentKey),
            equipmentPage);

        return Task.FromResult(keys.WithItems<SampleAnalysis>(
            [.. ofTenant.Where(analysis => keys.Items.Contains(analysis.EquipmentKey))]));
    }

    public Task<Page<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        VisibilityScope scope,
        string equipmentKey,
        PageRequest reportPage,
        CancellationToken cancellationToken)
    {
        TimesAPageOfReportsWasAsked++;
        PagesAsked.Add(reportPage);

        return Task.FromResult(Page<SampleAnalysis>.Of(
            In(scope)
                .Where(analysis => string.Equals(analysis.EquipmentKey, equipmentKey, StringComparison.Ordinal))
                .OrderByDescending(analysis => analysis.AnalysisEndDate)
                .ThenBy(analysis => analysis.ProtocoloLactec, StringComparer.Ordinal),
            reportPage));
    }

    public Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        VisibilityScope scope,
        string protocoloLactec,
        CancellationToken cancellationToken)
    {
        TimesOneProtocolWasAsked++;

        SampleAnalysis? found = In(scope)
            .FirstOrDefault(analysis => string.Equals(
                analysis.ProtocoloLactec, protocoloLactec, StringComparison.Ordinal));

        return Task.FromResult(found);
    }

    private IEnumerable<SampleAnalysis> In(VisibilityScope scope)
    {
        return _analyses.Where(analysis => scope.Includes(analysis.Company));
    }
}
