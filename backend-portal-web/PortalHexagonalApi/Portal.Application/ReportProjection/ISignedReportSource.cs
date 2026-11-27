using Portal.Application.LabResults;
using Portal.Application.ReportDocuments;

namespace Portal.Application.ReportProjection;

public sealed record SignedReportPointer(
    Guid ReportId,
    string Identification,
    DateTime IssueDate,
    Guid SignedFileId);

public sealed record SignedReportCursor(DateTime IssueDate, Guid ReportId);

public sealed record CompanyProtocol(
    string ProtocoloLactec,
    string Identification,
    string? SerialNumber,
    string Company,
    DateTime IssueDate,
    DateTime AnalysisEndDate,
    IReadOnlyList<ProtocolReport> Reports);

public interface ISignedReportSource
{
    Task<IReadOnlyList<SignedReportPointer>> NextSignedReportsAsync(
        string cnpj,
        DateTime? fromIssueDate,
        SignedReportCursor? after,
        int top,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<CompanyProtocol>> GetProtocolsOfCompanyAsync(
        string cnpj,
        CancellationToken cancellationToken);

    Task<SignedReportFindings> ReadAsync(Guid signedFileId, CancellationToken cancellationToken);
}
