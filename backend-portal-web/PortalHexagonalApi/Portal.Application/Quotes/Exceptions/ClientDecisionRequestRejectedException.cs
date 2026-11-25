namespace Portal.Application.Quotes.Exceptions;

public sealed class ClientDecisionRequestRejectedException(string message) : Exception(message)
{
}
