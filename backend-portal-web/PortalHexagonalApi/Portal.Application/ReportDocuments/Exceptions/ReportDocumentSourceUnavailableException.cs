namespace Portal.Application.ReportDocuments.Exceptions;

public sealed class ReportDocumentSourceUnavailableException : Exception
{
    public ReportDocumentSourceUnavailableException(string message)
        : base(message)
    {
    }

    public ReportDocumentSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
