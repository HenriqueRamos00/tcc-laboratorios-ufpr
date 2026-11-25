namespace Portal.Application.ReportDocuments.Exceptions;

public sealed class ReportArchiveTooLargeException : Exception
{
    public ReportArchiveTooLargeException(int reportCount, int maxReports)
        : base($"O equipamento tem {reportCount} laudos; o arquivo compactado aceita no máximo {maxReports}.")
    {
        ReportCount = reportCount;
        MaxReports = maxReports;
    }

    public int ReportCount { get; }

    public int MaxReports { get; }
}
