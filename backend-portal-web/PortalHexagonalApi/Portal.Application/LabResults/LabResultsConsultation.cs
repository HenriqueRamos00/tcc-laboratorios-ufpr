using Portal.Application.Nameplate;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Domain.Nameplate;

namespace Portal.Application.LabResults;

/// <summary>
/// A aplicacao atras da porta condutora unica. Este servico e o unico lugar que sabe a ordem
/// em que os atores conduzidos sao consultados: a fonte de ensaios responde primeiro, dentro
/// do escopo; o cadastro de equipamentos nao tem coluna de empresa e so e consultado depois,
/// com os numeros de serie que a primeira resposta trouxe.
/// </summary>
public sealed class LabResultsConsultation : IForConsultingLabResults
{
    public const int MaxAnalysesPerEquipment = 100;

    private static readonly PageRequest MostRecentAnalysisOnly = new(1, 1);
    private static readonly PageRequest MostRecentAnalysesOfEquipment = new(1, MaxAnalysesPerEquipment);

    private readonly ILabTestRepository _labTestRepository;
    private readonly IEquipmentNameplateRepository _equipmentNameplateRepository;
    private readonly IReportIssueRepository _reportIssues;

    public LabResultsConsultation(
        ILabTestRepository labTestRepository,
        IEquipmentNameplateRepository equipmentNameplateRepository,
        IReportIssueRepository reportIssues)
    {
        _labTestRepository = labTestRepository;
        _equipmentNameplateRepository = equipmentNameplateRepository;
        _reportIssues = reportIssues;
    }

    /// <summary>
    /// O cadastro e consultado uma vez, com todas as series da listagem. Perguntar uma vez
    /// por equipamento dentro do laco devolve a mesma resposta HTTP e faz um adaptador de
    /// banco abrir uma consulta por linha da tela.
    /// </summary>
    public async Task<Page<EquipmentWithNameplate>> ListEquipmentAsync(
        VisibilityScope scope,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(page);

        Page<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesAsync(scope, page, cancellationToken);

        IReadOnlyList<TestedEquipment> equipmentList = TestedEquipment.Gather(analyses.Items);

        IReadOnlyList<EquipmentNameplate> nameplates = await AskTheRegistryAboutAsync(
            equipmentList, cancellationToken);

        return analyses.WithItems<EquipmentWithNameplate>(
            [.. equipmentList.Select(equipment => Pair(equipment, nameplates))]);
    }

    public async Task<Page<EquipmentReportEntry>?> ListReportsAsync(
        VisibilityScope scope,
        string equipmentKey,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(page);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return null;

        Page<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(scope, equipmentKey, page, cancellationToken);

        TestedEquipment? equipment = TestedEquipment.Gather(analyses.Items)
            .FirstOrDefault(found => string.Equals(
                found.EquipmentKey, equipmentKey.Trim(), StringComparison.Ordinal));

        if (equipment is null)
            return analyses.TotalCount == 0 ? null : analyses.WithItems<EquipmentReportEntry>([]);

        IReadOnlySet<string> signed = await _reportIssues.GetIdentificationsWithSignedDocumentAsync(
            [.. equipment.Reports.Select(report => report.Identification).Distinct(StringComparer.Ordinal)],
            cancellationToken);

        return analyses.WithItems<EquipmentReportEntry>(
            [.. equipment.Reports.Select(report =>
                new EquipmentReportEntry(report, signed.Contains(report.Identification)))]);
    }

    /// <summary>
    /// Reusa a consulta por equipamento em vez de filtrar a lista inteira na memoria: e a
    /// mesma pergunta que a tela de indicadores faz, e um adaptador de banco vai responder
    /// com um WHERE em vez de trazer tudo para descartar quase tudo.
    ///
    /// O cadastro e consultado depois de o equipamento ter sido encontrado nas analises do
    /// escopo, e nunca antes. Equipamento fora do escopo devolve ausencia aqui, e a
    /// pergunta sobre a placa nem chega a ser feita.
    /// </summary>
    public async Task<EquipmentWithNameplate?> GetEquipmentAsync(
        VisibilityScope scope,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return null;

        Page<SampleAnalysis> mostRecent = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(scope, equipmentKey, MostRecentAnalysisOnly, cancellationToken);

        if (mostRecent.Items.Count == 0) return null;

        TestedEquipment equipment = TestedEquipment.Gather(mostRecent.Items)[0];

        return new EquipmentWithNameplate(
            equipment,
            equipment.SerialNumber is null
                ? null
                : await _equipmentNameplateRepository.GetNameplateOfAsync(
                    equipment.SerialNumber, cancellationToken),
            mostRecent.TotalCount);
    }

    public async Task<SampleHealthIndicators?> GetHealthIndicatorsAsync(
        HealthIndicatorsQuery query,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(query);
        ArgumentNullException.ThrowIfNull(query.Scope);

        if (string.IsNullOrWhiteSpace(query.EquipmentKey))
            return null;

        Page<SampleAnalysis> recentAnalyses = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(
                query.Scope, query.EquipmentKey, MostRecentAnalysesOfEquipment, cancellationToken);

        IReadOnlyList<SampleAnalysis> analyses = recentAnalyses.Items;

        if (analyses.Count == 0) return null;

        SampleAnalysis? selected = await ResolveSelectedAnalysisAsync(query, analyses, cancellationToken);

        EquipmentNameplate? nameplate = analyses[0].SerialNumber is null
            ? null
            : await _equipmentNameplateRepository.GetNameplateOfAsync(
                analyses[0].SerialNumber!, cancellationToken);

        string? physicalChemicalIdentification = (selected
            ?? analyses.FirstOrDefault(analysis => analysis.HasPhysicalChemicalProperty))?.Identification;
        string? gasIdentification = (selected
            ?? analyses.FirstOrDefault(analysis => analysis.HasDissolvedGas))?.Identification;

        ReportIssue? physicalChemicalReport = await CurrentIssueOfAsync(
            physicalChemicalIdentification, cancellationToken);
        ReportIssue? gasReport = string.Equals(
                gasIdentification, physicalChemicalIdentification, StringComparison.Ordinal)
            ? physicalChemicalReport
            : await CurrentIssueOfAsync(gasIdentification, cancellationToken);

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
            [.. analyses.SelectMany(analysis => analysis.CheckTotalizers())])
        {
            NominalVoltage = nameplate?.Voltage,
            PhysicalChemicalReport = physicalChemicalReport,
            GasReport = gasReport
        };
    }

    private async Task<ReportIssue?> CurrentIssueOfAsync(
        string? identification,
        CancellationToken cancellationToken)
    {
        return identification is null
            ? null
            : await _reportIssues.GetCurrentIssueOfAsync(identification, cancellationToken);
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
            .GetAnalysisByProtocolAsync(query.Scope, requestedProtocol, cancellationToken);

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
                    nameplate.SerialNumber, equipment.SerialNumber, StringComparison.Ordinal)),
            equipment.Reports.Count);
    }
}