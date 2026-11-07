using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>
/// Porta condutora: tudo o que a aplicacao oferece ao cliente que veio ver os ensaios do
/// transformador dele. Uma porta por ator, e nao uma por caso de uso.
///
/// Eram duas interfaces antes, uma para a listagem e outra para os indicadores, e o
/// comentario que justificava a separacao dizia haver outro ator na frente de cada uma.
/// Nao ha: as duas telas sao o mesmo cliente no mesmo portal, e o app Android, que seria o
/// segundo ator, nao chama nenhuma das duas. Porta por caso de uso dissolve o conceito de
/// porta, porque passa a nao existir diferenca entre uma porta e qualquer interface
/// desenhada em qualquer ponto do sistema.
///
/// O nome diz a intencao do ator, nao a tecnologia de quem atende nem o formato do
/// repositorio: quem esta do outro lado pode ser o navegador, o app ou a suite de testes.
/// </summary>
public interface IForConsultingLabResults
{
    /// <summary>
    /// Os equipamentos do inquilino, cada um com a placa que o cadastro afirma sobre ele.
    ///
    /// A lista continua nascendo do agrupamento das analises, e nao de uma consulta ao
    /// cadastro: o cadastro nao sabe de quem e o equipamento, e listar por ele entregaria o
    /// parque de todos os clientes. A ordem dos dois passos e o que garante o isolamento, e
    /// ela e parte do contrato desta operacao: as analises do inquilino respondem primeiro, e
    /// o cadastro so e consultado sobre os numeros de serie que elas revelaram.
    ///
    /// A placa e nula com frequencia e isso e resposta bem sucedida.
    /// <see cref="TestedEquipment"/> vem dos laudos e existe sempre; o cadastro conhece 86%
    /// das series e nem sempre concorda consigo mesmo.
    /// </summary>
    Task<IReadOnlyList<EquipmentWithNameplate>> ListEquipmentAsync(
        string tenant,
        CancellationToken cancellationToken);

    Task<EquipmentWithNameplate?> GetEquipmentAsync(
        string tenant,
        string equipmentKey,
        CancellationToken cancellationToken);

    Task<SampleHealthIndicators?> GetHealthIndicatorsAsync(
        HealthIndicatorsQuery query,
        CancellationToken cancellationToken);
}
