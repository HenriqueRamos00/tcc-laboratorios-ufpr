namespace Portal.Application.Internal;

public interface IBackendLabRequestContext
{
    string? AccessToken { get; }
    string CorrelationId { get; }
}
