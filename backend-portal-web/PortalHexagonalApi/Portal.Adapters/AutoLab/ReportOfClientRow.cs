using Portal.Application.CompanyRecords;

namespace Portal.Adapters.AutoLab;

public sealed record ReportOfClientRow(Guid? ClientId, CompanyReportRow Report);

public sealed record SignedReportRow(Guid ReportId, string? Identification, DateTime IssueDate, Guid SignedFileId);

public sealed record ProtocolSummaryRow(
    string? ProtocoloLactec,
    string? Identificacao,
    string? NumSerie,
    string? EmpresaTrafo,
    DateTime? DataEmissao,
    DateTime? DataFinalAnalise);

public sealed record ProtocolReportRow(string? ProtocoloLactec, string? Identificacao, DateTime? DataEmissao);
