using System.Text.RegularExpressions;

using Portal.Application.LabResults.Exceptions;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Reports;
using Portal.Domain.LabResults;
using Portal.Domain.Reports;

namespace Portal.Application.LabResults;

/// <summary>Expõe ao consumidor os dados publicados e autorizados pelo Backend Lab.</summary>
public sealed partial class ClientLabResultsConsultation(
    IClientLabResultsRepository results,
    IReportDocumentRepository reports) : IForConsultingClientLabResults
{
    private const int SpringMaximumPageSize = 100;
    private const string SaoPauloTimeZone = "America/Sao_Paulo";
    private static readonly TimeZoneInfo PortalTimeZone = TimeZoneInfo.FindSystemTimeZoneById(SaoPauloTimeZone);
    private static readonly PageRequest FirstHundred = new(1, SpringMaximumPageSize);

    public Task<Page<ClientEquipment>> ListEquipmentAsync(
        EquipmentSearch? search, PageRequest page, CancellationToken cancellationToken)
    {
        ValidatePage(page);
        return results.ListEquipmentAsync(search, page, cancellationToken);
    }

    public Task<ClientEquipment?> GetEquipmentAsync(string equipmentId, CancellationToken cancellationToken) =>
        results.GetEquipmentAsync(RequiredId(equipmentId), cancellationToken);

    public Task<Page<ClientAnalysis>?> ListAnalysesOfEquipmentAsync(
        string equipmentId,
        PageRequest page,
        CancellationToken cancellationToken)
    {
        ValidatePage(page);
        return results.ListAnalysesOfEquipmentAsync(RequiredId(equipmentId), page, cancellationToken);
    }

    public Task<ClientReportDetail?> GetReportDetailAsync(string reportId, CancellationToken cancellationToken) =>
        results.GetReportDetailAsync(RequiredId(reportId), cancellationToken);

    public async Task<Stream?> GetReportFileAsync(
        string equipmentId,
        string reportId,
        CancellationToken cancellationToken)
    {
        string requestedEquipmentId = RequiredId(equipmentId);
        string requestedReportId = RequiredId(reportId);
        ClientReportDetail? detail = await results.GetReportDetailAsync(requestedReportId, cancellationToken);

        if (detail is null) return null;

        if (detail.Analysis.Equipment?.Id is not string associatedEquipmentId
            || !string.Equals(associatedEquipmentId, requestedEquipmentId, StringComparison.Ordinal))
            return null;

        PublishedReportFile? file = await reports.GetFileAsync(requestedReportId, cancellationToken);
        return file is null ? null : new MemoryStream(file.Content, writable: false);
    }

    public async Task<EquipmentReportArchive?> OpenReportArchiveAsync(
        string equipmentId,
        CancellationToken cancellationToken)
    {
        string requestedEquipmentId = RequiredId(equipmentId);
        Page<ClientAnalysis>? first = await results.ListAnalysesOfEquipmentAsync(
            requestedEquipmentId, FirstHundred, cancellationToken);

        if (first is null) return null;

        if (first.TotalCount > EquipmentReportArchive.MaxReports)
            throw new ReportArchiveTooLargeException(first.TotalCount, EquipmentReportArchive.MaxReports);

        List<ClientAnalysis> analyses = [.. first.Items];

        for (int pageNumber = 2; analyses.Count < first.TotalCount && first.Items.Count > 0; pageNumber++)
        {
            Page<ClientAnalysis>? next = await results.ListAnalysesOfEquipmentAsync(
                requestedEquipmentId, new PageRequest(pageNumber, SpringMaximumPageSize), cancellationToken);

            if (next is null || next.Items.Count == 0) break;

            analyses.AddRange(next.Items);
        }

        return new EquipmentReportArchive(
            requestedEquipmentId,
            analyses
                .Where(analysis => analysis.PdfAvailable)
                .OrderByDescending(analysis => analysis.PublishedAt)
                .Select(analysis => new ReportArchiveCandidate(
                    analysis.ReportId,
                    ArchiveTypeOf(analysis.AnalysisType),
                    DateOnly.FromDateTime(TimeZoneInfo.ConvertTime(analysis.PublishedAt, PortalTimeZone).DateTime),
                    analysis.ReportCode)),
            (reportId, token) => GetReportFileAsync(requestedEquipmentId, reportId, token));
    }

    private static ReportAnalysisType ArchiveTypeOf(string? analysisType) => analysisType switch
    {
        "physical-chemical" => ReportAnalysisType.PhysicalChemical,
        "chromatography" => ReportAnalysisType.Chromatography,
        _ => ReportAnalysisType.Other
    };

    public async Task<SampleHealthIndicators?> GetHealthIndicatorsAsync(
        string equipmentId,
        string? selectedAnalysisId,
        CancellationToken cancellationToken)
    {
        string requestedEquipmentId = RequiredId(equipmentId);
        ClientEquipment? equipment = await results.GetEquipmentAsync(requestedEquipmentId, cancellationToken);
        if (equipment is null) return null;

        Page<ClientAnalysis>? page = await results.ListAnalysesOfEquipmentAsync(
            requestedEquipmentId, FirstHundred, cancellationToken);
        if (page is null) return null;

        IReadOnlyList<ClientAnalysis> analyses = page.Items;
        ClientAnalysis? selected = await FindSelectedAnalysisAsync(
            requestedEquipmentId, selectedAnalysisId, analyses, cancellationToken);
        if (!string.IsNullOrWhiteSpace(selectedAnalysisId) && selected is null) return null;

        if (analyses.Count == 0)
        {
            return new SampleHealthIndicators(
                equipment.SerialNumber,
                equipment.Tag ?? equipment.SerialNumber ?? string.Empty,
                AnalysisSeries.Build(requestedEquipmentId, []),
                AnalysisSeries.Build(requestedEquipmentId, []),
                null,
                [],
                [])
            {
                Tag = equipment.Tag,
                NominalVoltage = VoltageText(equipment.PrimaryVoltageKv),
                Oil = InsulatingOil.Recognize(equipment.OilType)
            };
        }

        List<ClientAnalysis> reportSources = [.. analyses];
        if (selected is not null && !reportSources.Any(analysis =>
            string.Equals(analysis.ReportId, selected.ReportId, StringComparison.Ordinal)))
            reportSources.Add(selected);

        List<SampleAnalysis> mapped = reportSources.Select(analysis => ToSampleAnalysis(requestedEquipmentId, analysis)).ToList();
        SampleAnalysis? selectedMapped = selected is null ? null : ToSampleAnalysis(requestedEquipmentId, selected);
        string identification = selectedMapped?.Identification ?? mapped[0].Identification;

        ClientAnalysis? physicalChemicalReport = FindReportSource(
            reportSources, mapped, selectedMapped, ReportAnalysisType.PhysicalChemical);
        ClientAnalysis? gasReport = FindReportSource(
            reportSources, mapped, selectedMapped, ReportAnalysisType.Chromatography);

        return new SampleHealthIndicators(
            equipment.SerialNumber,
            identification,
            AnalysisSeries.Build(requestedEquipmentId, mapped.Where(analysis => analysis.HasPhysicalChemicalProperty)),
            AnalysisSeries.Build(requestedEquipmentId, mapped.Where(analysis => analysis.HasDissolvedGas)),
            selectedMapped,
            GatherUnmappedResults(mapped),
            [.. mapped.SelectMany(analysis => analysis.CheckTotalizers())])
        {
            Tag = equipment.Tag,
            NominalVoltage = VoltageText(equipment.PrimaryVoltageKv),
            Oil = InsulatingOil.Recognize(equipment.OilType),
            PhysicalChemicalReports = ConclusionsOf(physicalChemicalReport),
            GasReports = ConclusionsOf(gasReport)
        };
    }

    private async Task<ClientAnalysis?> FindSelectedAnalysisAsync(
        string equipmentId,
        string? selectedAnalysisId,
        IReadOnlyList<ClientAnalysis> listed,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(selectedAnalysisId)) return null;

        ClientAnalysis? inPage = listed.FirstOrDefault(analysis =>
            string.Equals(analysis.ReportId, selectedAnalysisId, StringComparison.Ordinal));
        if (inPage is not null) return inPage;

        ClientAnalysis? selected = await results.GetAnalysisAsync(selectedAnalysisId, cancellationToken);
        return selected?.Equipment is not null
            && string.Equals(selected.Equipment.Id, equipmentId, StringComparison.Ordinal)
                ? selected
                : null;
    }

    private static SampleAnalysis ToSampleAnalysis(string equipmentId, ClientAnalysis analysis)
    {
        DateTimeOffset publishedAt = analysis.PublishedAt;
        DateTime localPublicationDate = TimeZoneInfo.ConvertTime(publishedAt, PortalTimeZone).DateTime;

        return new SampleAnalysis(
            analysis.Id,
            analysis.ReportId,
            analysis.TestId,
            analysis.Quote.Protocol,
            equipmentId,
            analysis.ReportCode,
            analysis.Company.Name,
            publishedAt,
            localPublicationDate,
            analysis.Measurements.Select(ClientMeasurementMapper.Map));
    }

    private static ClientAnalysis? FindReportSource(
        IReadOnlyList<ClientAnalysis> sources,
        IReadOnlyList<SampleAnalysis> mapped,
        SampleAnalysis? selected,
        ReportAnalysisType type)
    {
        bool HasType(SampleAnalysis analysis) => type switch
        {
            ReportAnalysisType.PhysicalChemical => analysis.HasPhysicalChemicalProperty,
            ReportAnalysisType.Chromatography => analysis.HasDissolvedGas,
            _ => false
        };

        SampleAnalysis? chosen = selected is not null && HasType(selected)
            ? selected
            : mapped.FirstOrDefault(HasType);

        return chosen is null
            ? null
            : sources.FirstOrDefault(source => string.Equals(
                source.ReportId, chosen.ReportId, StringComparison.Ordinal));
    }

    private static IReadOnlyList<ReportConclusion> ConclusionsOf(ClientAnalysis? analysis) => analysis is null
        ? []
        :
        [
            new ReportConclusion(
                analysis.ReportCode,
                TimeZoneInfo.ConvertTime(analysis.PublishedAt, PortalTimeZone).DateTime,
                null,
                analysis.TechnicalClassification,
                ParagraphsOf(analysis.Conclusion))
        ];

    private static IReadOnlyList<string> ParagraphsOf(string? text) => string.IsNullOrWhiteSpace(text)
        ? []
        : [.. BlankLine().Split(text)
            .Select(paragraph => paragraph.Trim())
            .Where(paragraph => paragraph.Length > 0)];

    [GeneratedRegex(@"\r?\n[ \t]*\r?\n")]
    private static partial Regex BlankLine();

    private static IReadOnlyList<UnreadableResult> GatherUnmappedResults(IEnumerable<SampleAnalysis> analyses)
    {
        return [.. analyses
            .SelectMany(analysis => analysis.Measurements.Select(measurement => new { analysis, measurement }))
            .Where(pair => pair.measurement.MeasuredItem.Role == MeasuredItemKind.Other
                || pair.measurement.Value is MeasuredValue.UninterpretedText)
            .Select(pair => new UnreadableResult(
                pair.measurement.MeasuredItem.SourceCode ?? pair.measurement.MeasuredItem.Code,
                pair.measurement.MeasuredItem.Label,
                ResultText(pair.measurement.Value),
                pair.analysis.ProtocoloLactec,
                pair.analysis.AnalysisId,
                pair.measurement.MeasuredItem.Unit,
                pair.measurement.MeasuredItem.TestMethod,
                pair.measurement.MeasuredItem.RegulatoryLimit,
                ResultKind(pair.measurement.Value)))];
    }

    private static string ResultText(MeasuredValue result) => result switch
    {
        MeasuredValue.Quantity quantity => ClientMeasurementMapper.DecimalText(quantity.NumericValue),
        MeasuredValue.NotDetected => "not-detected",
        MeasuredValue.TestNotPerformed => "not-performed",
        MeasuredValue.UninterpretedText text => text.OriginalText,
        _ => string.Empty
    };

    private static string ResultKind(MeasuredValue result) => result switch
    {
        MeasuredValue.Quantity => "quantity",
        MeasuredValue.NotDetected => "not-detected",
        MeasuredValue.TestNotPerformed => "not-performed",
        MeasuredValue.UninterpretedText => "text",
        _ => "unknown"
    };

    private static string? VoltageText(decimal? voltage) => voltage is null
        ? null
        : $"{ClientMeasurementMapper.DecimalText(voltage.Value)} kV";

    private static string RequiredId(string? value) => string.IsNullOrWhiteSpace(value)
        ? throw new ArgumentException("O identificador não pode ser vazio.", nameof(value))
        : value.Trim();

    private static void ValidatePage(PageRequest page)
    {
        ArgumentNullException.ThrowIfNull(page);
        if (page.PageSize > SpringMaximumPageSize) throw new ClientLabRequestRejectedException();
    }
}
