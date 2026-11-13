namespace Portal.Application.ReportDocuments;

public sealed record SignedReportDocument(string? FileName, byte[] Content);
