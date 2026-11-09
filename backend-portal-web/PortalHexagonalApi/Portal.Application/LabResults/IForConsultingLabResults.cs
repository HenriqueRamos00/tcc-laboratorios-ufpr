using Portal.Application.Paging;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>
/// Porta condutora: tudo o que a aplicacao oferece ao cliente que veio ver os ensaios do
/// transformador dele. Uma porta por ator, e nao uma por caso de uso.
///
/// O nome diz a intencao do ator, nao a tecnologia de quem atende nem o formato do
/// repositorio: quem esta do outro lado pode ser o navegador, o app ou a suite de testes.
/// </summary>
public interface IForConsultingLabResults
{
    /// <summary>
    /// Os equipamentos do escopo, cada um com a placa que o cadastro afirma sobre ele.
    ///
    /// A lista continua nascendo do agrupamento das analises, e nao de uma consulta ao
    /// cadastro: o cadastro nao sabe de quem e o equipamento. As analises do escopo
    /// respondem primeiro, e o cadastro so e consultado sobre os numeros de serie que elas
    /// revelaram.
    ///
    /// A placa e nula com frequencia e isso e resposta bem sucedida.
    /// </summary>
    Task<Page<EquipmentWithNameplate>> ListEquipmentAsync(
        VisibilityScope scope,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<EquipmentWithNameplate?> GetEquipmentAsync(
        VisibilityScope scope,
        string equipmentKey,
        CancellationToken cancellationToken);

    Task<Page<EquipmentReportEntry>?> ListReportsAsync(
        VisibilityScope scope,
        string equipmentKey,
        PageRequest page,
        CancellationToken cancellationToken);

    Task<SampleHealthIndicators?> GetHealthIndicatorsAsync(
        HealthIndicatorsQuery query,
        CancellationToken cancellationToken);
}
