namespace Portal.Application.ReportProjection.Exceptions;

public sealed class ReportProjectionUnavailableException : Exception
{
    public ReportProjectionUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
