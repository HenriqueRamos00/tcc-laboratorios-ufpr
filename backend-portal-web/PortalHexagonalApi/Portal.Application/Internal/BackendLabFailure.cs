namespace Portal.Application.Internal;

public enum BackendLabFailureKind
{
    InvalidInput,
    Unauthorized,
    Forbidden,
    NotFound,
    Conflict,
    InvalidResponse,
    Unavailable,
    Timeout
}

public sealed record BackendLabFailure(
    BackendLabFailureKind Kind,
    string Message,
    string? WwwAuthenticate = null);

public sealed record BackendLabResult<T>(T? Value, BackendLabFailure? Failure)
{
    public bool IsSuccess => Failure is null;

    public static BackendLabResult<T> Success(T value) => new(value, null);

    public static BackendLabResult<T> Failed(BackendLabFailure failure) => new(default, failure);
}
