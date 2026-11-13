namespace Portal.Application.Reports.Exceptions;

public sealed class ReportSourceUnavailableException : Exception
{
    public ReportSourceUnavailableException(string message)
        : base(message)
    {
    }

    public ReportSourceUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
