using Portal.Application.LabResults;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;

namespace Portal.Application.ReportDocuments;

public sealed class SignedReportDownload : IForDownloadingSignedReports
{
    private readonly ILabTestRepository _labTestRepository;
    private readonly IReportIssueRepository _reportIssues;

    public SignedReportDownload(
        ILabTestRepository labTestRepository,
        IReportIssueRepository reportIssues)
    {
        _labTestRepository = labTestRepository;
        _reportIssues = reportIssues;
    }

    public async Task<SignedReportDocument?> GetSignedReportAsync(
        VisibilityScope scope,
        string equipmentKey,
        string protocoloLactec,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (string.IsNullOrWhiteSpace(equipmentKey) || string.IsNullOrWhiteSpace(protocoloLactec))
            return null;

        SampleAnalysis? analysis = await _labTestRepository
            .GetAnalysisByProtocolAsync(scope, protocoloLactec.Trim(), cancellationToken);

        if (analysis is null
            || !string.Equals(analysis.EquipmentKey, equipmentKey.Trim(), StringComparison.Ordinal)
            || !string.Equals(analysis.ProtocoloLactec, protocoloLactec.Trim(), StringComparison.Ordinal))
            return null;

        return await _reportIssues
            .GetSignedDocumentOfAsync(analysis.Identification, cancellationToken);
    }
}
