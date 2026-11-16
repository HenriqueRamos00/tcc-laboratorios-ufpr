namespace Portal.Application.LabResults.Exceptions;

public sealed class ClientLabSourceUnavailableException : Exception
{
    public ClientLabSourceUnavailableException(string message, Exception? innerException = null)
        : base(message, innerException) { }
}
