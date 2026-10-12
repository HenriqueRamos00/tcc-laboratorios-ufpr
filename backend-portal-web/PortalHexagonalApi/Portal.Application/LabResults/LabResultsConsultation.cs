using Portal.Domain.LabResults;

namespace Portal.Application.LabResults;

/// <summary>
/// A aplicacao atras da porta condutora unica. As tres operacoes compartilham a mesma
/// guarda de inquilino, que e justamente o que estava copiado em dois servicos enquanto a
/// porta estava partida em duas.
/// </summary>
public sealed class LabResultsConsultation : IForConsultingLabResults
{
    private readonly ILabTestRepository _labTestRepository;

    public LabResultsConsultation(ILabTestRepository labTestRepository)
    {
        _labTestRepository = labTestRepository;
    }

    public async Task<IReadOnlyList<TestedEquipment>> ListEquipmentAsync(
        string tenant,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        IReadOnlyList<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfTenantAsync(tenant, cancellationToken);

        return TestedEquipment.Gather(analyses);
    }

    /// <summary>
    /// Reusa a consulta por equipamento em vez de filtrar a lista inteira na memoria: e a
    /// mesma pergunta que a tela de indicadores faz, e um adaptador de banco vai responder
    /// com um WHERE em vez de trazer o cliente todo para descartar quase tudo.
    /// </summary>
    public async Task<TestedEquipment?> GetEquipmentAsync(
        string tenant,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        RequireTenant(tenant);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return null;

        IReadOnlyList<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(tenant, equipmentKey, cancellationToken);

        return analyses.Count == 0
            ? null
            : TestedEquipment.Gather(analyses)[0];
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

    private static void RequireTenant(string tenant)
    {
        if (string.IsNullOrWhiteSpace(tenant))
            throw new ArgumentException("Consulta sem inquilino.", nameof(tenant));
    }
}
