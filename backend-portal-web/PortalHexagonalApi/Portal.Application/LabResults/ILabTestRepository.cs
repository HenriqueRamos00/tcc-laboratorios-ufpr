using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>
/// Porta conduzida: o que a aplicacao exige de quem guarda os ensaios laboratoriais. Uma
/// porta por ator externo, e nao uma por tabela: as duas views do AutoLAB sao o mesmo ator.
///
/// O inquilino e parametro de toda consulta, e nao um filtro aplicado depois. Quem escreve
/// um adaptador novo nao tem como esquecer de filtrar: a assinatura nao permite pedir sem
/// dizer por quem se pergunta.
///
/// O equipamento vem pela chave de <see cref="SampleAnalysis.EquipmentKey"/>, e nao
/// pelo numero de serie. A porta nao pode exigir um numero de serie que a fonte nao garante:
/// ha laudo real com a coluna em branco, e uma porta que pede serie torna esse laudo
/// inalcancavel por contrato, nao por defeito de adaptador.
/// </summary>
public interface ILabTestRepository
{
    /// <summary>
    /// Todas as analises do inquilino, de todos os equipamentos dele. A listagem de
    /// equipamentos nasce daqui porque o AutoLAB nao tem cadastro de equipamento: nao existe
    /// o que pedir sem passar pelas analises.
    /// </summary>
    Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfTenantAsync(
        string tenant,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<SampleAnalysis>> GetAnalysesOfEquipmentAsync(
        string tenant,
        string equipmentKey,
        CancellationToken cancellationToken);

    Task<SampleAnalysis?> GetAnalysisByProtocolAsync(
        string tenant,
        string protocoloLactec,
        CancellationToken cancellationToken);
}
