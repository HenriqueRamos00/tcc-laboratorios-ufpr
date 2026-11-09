using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>
/// Porta conduzida: o que a aplicacao exige de quem guarda os ensaios laboratoriais. Uma
/// porta por ator externo, e nao uma por tabela: as duas views do AutoLAB sao o mesmo ator.
///
/// O equipamento vem pela chave de <see cref="SampleAnalysis.EquipmentKey"/>, e nao
/// pelo numero de serie. A porta nao pode exigir um numero de serie que a fonte nao garante:
/// ha laudo real com a coluna em branco, e uma porta que pede serie torna esse laudo
/// inalcancavel por contrato, nao por defeito de adaptador.
/// </summary>
public interface ILabTestRepository
{
    Task<Page<SampleAnalysis>> GetAnalysesAsync(
        VisibilityScope scope,
        PageRequest equipmentPage,
        CancellationToken cancellationToken);

    Task<Page<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        VisibilityScope scope,
        string equipmentKey,
        PageRequest reportPage,
        CancellationToken cancellationToken);

    Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        VisibilityScope scope,
        string protocoloLactec,
        CancellationToken cancellationToken);
}
