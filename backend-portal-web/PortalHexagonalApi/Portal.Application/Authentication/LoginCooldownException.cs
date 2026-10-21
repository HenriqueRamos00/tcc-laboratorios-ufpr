namespace Portal.Application.Authentication;

public sealed class LoginCooldownException(long retryAfterSeconds) : Exception("Login temporariamente bloqueado.")
{
    public long RetryAfterSeconds { get; } = retryAfterSeconds;
}
