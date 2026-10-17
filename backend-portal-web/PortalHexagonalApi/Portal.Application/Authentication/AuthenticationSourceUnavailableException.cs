namespace Portal.Application.Authentication;

public sealed class AuthenticationSourceUnavailableException : Exception
{
    public AuthenticationSourceUnavailableException(string message, Exception? innerException = null)
        : base(message, innerException)
    {
    }
}
