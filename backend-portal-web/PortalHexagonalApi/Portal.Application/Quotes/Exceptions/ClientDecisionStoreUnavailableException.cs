namespace Portal.Application.Quotes.Exceptions;

public sealed class ClientDecisionStoreUnavailableException : Exception
{
    public ClientDecisionStoreUnavailableException(string message, bool connected = true)
        : base(message)
    {
        Connected = connected;
    }

    public ClientDecisionStoreUnavailableException(string message, Exception innerException)
        : base(message, innerException)
    {
        Connected = true;
    }

    public bool Connected { get; }
}
