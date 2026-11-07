using Portal.Application.Nameplate;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

namespace Portal.Application.LabResults;

/// <summary>
/// A aplicacao atras da porta condutora unica. As tres operacoes compartilham a mesma
/// guarda de inquilino, que e justamente o que estava copiado em dois servicos enquanto a
/// porta estava partida em duas.
///
/// Sao dois atores conduzidos, e este servico e o unico lugar que sabe a ordem em que eles
/// sao consultados. A ordem e a regra de isolamento desta fatia: a fonte de ensaios e
/// escopada por inquilino e responde primeiro; o cadastro de equipamentos nao tem coluna de
/// empresa e so e consultado depois, com os numeros de serie que a primeira resposta trouxe.
/// Trocar a ordem, ou consultar o cadastro com a chave que chegou na rota, devolve exatamente
/// a mesma resposta HTTP para uma requisicao legitima e entrega a placa do transformador de
/// outro cliente para uma requisicao forjada.
/// </summary>
public sealed class LabResultsConsultation : IForConsultingLabResults
{
    private readonly ILabTestRepository _labTestRepository;
    private readonly IEquipmentNameplateRepository _equipmentNameplateRepository;

    public LabResultsConsultation(
        ILabTestRepository labTestRepository,
        IEquipmentNameplateRepository equipmentNameplateRepository)
    {
        _labTestRepository = labTestRepository;
        _equipmentNameplateRepository = equipmentNameplateRepository;
    }

    /// <summary>
    /// O cadastro e consultado uma vez, com todas as series da listagem. Perguntar uma vez
    /// por equipamento dentro do laco devolve a mesma resposta HTTP e faz um adaptador de
    /// banco abrir uma consulta por linha da tela.
    /// </summary>
    public async Task<IReadOnlyList<EquipmentWithNameplate>> ListEquipmentAsync(
        string tenant,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        IReadOnlyList<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfTenantAsync(tenant, cancellationToken);

        IReadOnlyList<TestedEquipment> equipmentList = TestedEquipment.Gather(analyses);

        IReadOnlyList<EquipmentNameplate> nameplates = await AskTheRegistryAboutAsync(
            equipmentList, cancellationToken);

        return [.. equipmentList.Select(equipment => Pair(equipment, nameplates))];
    }

    /// <summary>
    /// Reusa a consulta por equipamento em vez de filtrar a lista inteira na memoria: e a
    /// mesma pergunta que a tela de indicadores faz, e um adaptador de banco vai responder
    /// com um WHERE em vez de trazer o cliente todo para descartar quase tudo.
    ///
    /// O cadastro e consultado depois de o equipamento ter sido encontrado nas analises do
    /// inquilino, e nunca antes. Equipamento de outro cliente devolve ausencia aqui, e a
    /// pergunta sobre a placa nem chega a ser feita.
    /// </summary>
    public async Task<EquipmentWithNameplate?> GetEquipmentAsync(
        string tenant,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return null;

        IReadOnlyList<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(tenant, equipmentKey, cancellationToken);

        if (analyses.Count == 0) return null;

        TestedEquipment equipment = TestedEquipment.Gather(analyses)[0];

        return new EquipmentWithNameplate(
            equipment,
            equipment.SerialNumber is null
                ? null
                : await _equipmentNameplateRepository.GetNameplateOfAsync(
                    equipment.SerialNumber, cancellationToken));
    }

    public async Task<SampleHealthIndicators?> GetHealthIndicatorsAsync(
        HealthIndicatorsQuery query,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(query);

        RequireTenant(query.Tenant);

        if (string.IsNullOrWhiteSpace(query.EquipmentKey))
            return null;

        IReadOnlyList<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(query.Tenant, query.EquipmentKey, cancellationToken);

        if (analyses.Count == 0) return null;

        SampleAnalysis? selected = await ResolveSelectedAnalysisAsync(query, analyses, cancellationToken);

        return new SampleHealthIndicators(
            analyses[0].SerialNumber,
            analyses[0].Identification,
            AnalysisSeries.Build(
                analyses[0].EquipmentKey,
                analyses.Where(analysis => analysis.HasPhysicalChemicalProperty)),
            AnalysisSeries.Build(
                analyses[0].EquipmentKey,
                analyses.Where(analysis => analysis.HasDissolvedGas)),
            selected,
            GatherUnreadableResults(analyses),
            [.. analyses.SelectMany(analysis => analysis.CheckTotalizers())]);
    }

    /// <summary>
    /// A analise escolhida na URL e aceita somente quando pertence ao mesmo equipamento
    /// consultado. Sem essa comparacao, um protocolo valido de outro transformador nomearia
    /// a migalha de pao de uma serie que nao e a dele.
    ///
    /// A comparacao e pela chave do equipamento, nunca pelo numero de serie cru: dois laudos
    /// de transformadores diferentes podem ter os dois a serie em branco, e comparar nulo com
    /// nulo da igual, o que entregaria o laudo de um equipamento na tela de outro.
    /// </summary>
    private async Task<SampleAnalysis?> ResolveSelectedAnalysisAsync(
        HealthIndicatorsQuery query,
        IReadOnlyList<SampleAnalysis> analyses,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(query.SelectedProtocoloLactec)) return null;

        string requestedProtocol = query.SelectedProtocoloLactec.Trim();

        SampleAnalysis? inTheSeries = analyses.FirstOrDefault(analysis =>
            string.Equals(analysis.ProtocoloLactec, requestedProtocol, StringComparison.Ordinal));

        if (inTheSeries is not null) return inTheSeries;

        SampleAnalysis? stranger = await _labTestRepository
            .GetAnalysisByProtocolAsync(query.Tenant, requestedProtocol, cancellationToken);

        return stranger is not null
            && string.Equals(
                stranger.EquipmentKey,
                analyses[0].EquipmentKey,
                StringComparison.Ordinal)
            ? stranger
            : null;
    }

    private static IReadOnlyList<UnreadableResult> GatherUnreadableResults(
        IReadOnlyList<SampleAnalysis> analyses)
    {
        return [.. analyses
            .SelectMany(analysis => analysis.Measurements.Select(measurement => new { analysis, measurement }))
            .Where(pair => pair.measurement.Value is MeasuredValue.UninterpretedText)
            .Select(pair => new UnreadableResult(
                pair.measurement.MeasuredItem.Code,
                pair.measurement.MeasuredItem.Label,
                ((MeasuredValue.UninterpretedText)pair.measurement.Value).OriginalText,
                pair.analysis.ProtocoloLactec))];
    }

    /// <summary>
    /// Pergunta a placa das series que estes equipamentos tem, e de nenhuma outra. A chave e
    /// <see cref="TestedEquipment.SerialNumber"/> e nunca
    /// <see cref="TestedEquipment.EquipmentKey"/>: a chave cai no protocolo Lactec quando a
    /// serie vem em branco, e protocolo nao e numero de serie. Perguntar o cadastro por
    /// "01439/2026-004" so pode produzir casamento falso, e o laudo sem serie fica sem placa,
    /// que e a resposta honesta.
    ///
    /// Equipamento sem serie nenhuma nao entra na pergunta, e lista vazia nao chega a
    /// perguntar: uma clausula IN vazia e escrita de SQL que alguns geradores transformam em
    /// consulta sem filtro, e uma consulta sem filtro aqui le o cadastro inteiro.
    /// </summary>
    private async Task<IReadOnlyList<EquipmentNameplate>> AskTheRegistryAboutAsync(
        IReadOnlyList<TestedEquipment> equipmentList,
        CancellationToken cancellationToken)
    {
        string[] serialNumbers = [.. equipmentList
            .Select(equipment => equipment.SerialNumber)
            .OfType<string>()
            .Distinct(StringComparer.Ordinal)];

        return serialNumbers.Length == 0
            ? []
            : await _equipmentNameplateRepository.GetNameplatesOfAsync(
                serialNumbers, cancellationToken);
    }

    private static EquipmentWithNameplate Pair(
        TestedEquipment equipment,
        IReadOnlyList<EquipmentNameplate> nameplates)
    {
        return new EquipmentWithNameplate(
            equipment,
            equipment.SerialNumber is null
                ? null
                : nameplates.FirstOrDefault(nameplate => string.Equals(
                    nameplate.SerialNumber, equipment.SerialNumber, StringComparison.Ordinal)));
    }

    private static void RequireTenant(string tenant)
    {
        if (string.IsNullOrWhiteSpace(tenant))
            throw new ArgumentException("Consulta sem inquilino.", nameof(tenant));
    }
}