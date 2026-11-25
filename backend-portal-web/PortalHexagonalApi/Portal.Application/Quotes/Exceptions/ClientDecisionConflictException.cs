namespace Portal.Application.Quotes.Exceptions;

public sealed class ClientDecisionConflictException : Exception
{
    public ClientDecisionConflictException(string message, Exception innerException)
        : base(message, innerException)
    {
    }
}
