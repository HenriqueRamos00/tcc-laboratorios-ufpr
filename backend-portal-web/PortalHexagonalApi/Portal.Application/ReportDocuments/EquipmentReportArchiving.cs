using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments.Exceptions;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Application.ReportDocuments;

public sealed class EquipmentReportArchiving : IForArchivingEquipmentReports
{
    private static readonly PageRequest WholeArchive = new(1, EquipmentReportArchive.MaxReports);

    private readonly ILabTestRepository _labTestRepository;
    private readonly IReportIssueRepository _reportIssues;

    public EquipmentReportArchiving(
        ILabTestRepository labTestRepository,
        IReportIssueRepository reportIssues)
    {
        _labTestRepository = labTestRepository;
        _reportIssues = reportIssues;
    }

    public async Task<EquipmentReportArchive?> OpenArchiveAsync(
        CompanyScope scope,
        string equipmentKey,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(equipmentKey)) return null;

        string wantedKey = equipmentKey.Trim();

        Page<SampleAnalysis> analyses = await _labTestRepository
            .GetAnalysesOfEquipmentAsync(scope, wantedKey, WholeArchive, cancellationToken);

        if (analyses.Items.Count == 0) return null;

        if (analyses.TotalCount > EquipmentReportArchive.MaxReports)
            throw new ReportArchiveTooLargeException(analyses.TotalCount, EquipmentReportArchive.MaxReports);

        IReadOnlyList<EquipmentReport> reports = TestedEquipment.Gather(analyses.Items)
            .FirstOrDefault(found => string.Equals(found.EquipmentKey, wantedKey, StringComparison.Ordinal))
            ?.Reports ?? [];

        IReadOnlySet<string> signed = reports.Count == 0
            ? new HashSet<string>(StringComparer.Ordinal)
            : await _reportIssues.GetIdentificationsWithSignedDocumentAsync(
                scope,
                [.. reports.Select(report => report.Identification).Distinct(StringComparer.Ordinal)],
                cancellationToken);

        return new EquipmentReportArchive(
            wantedKey,
            reports
                .Where(report => signed.Contains(report.Identification))
                .Select(report => new ReportArchiveCandidate(
                    report.Identification,
                    report.AnalysisType,
                    DateOnly.FromDateTime(report.AnalysisEndDate),
                    report.Identification)),
            async (identification, token) =>
            {
                SignedReportDocument? document = await _reportIssues
                    .GetSignedDocumentOfAsync(scope, identification, token);

                return document is null ? null : new MemoryStream(document.Content, writable: false);
            });
    }
}
