using System.Diagnostics;
using Portal.Application.Internal;

namespace Portal.Api.Security;

public sealed class HttpBackendLabRequestContext : IBackendLabRequestContext
{
    private readonly IHttpContextAccessor _httpContextAccessor;

    public HttpBackendLabRequestContext(IHttpContextAccessor httpContextAccessor)
    {
        _httpContextAccessor = httpContextAccessor;
    }

    public string? AccessToken
    {
        get
        {
            string? authorization = _httpContextAccessor.HttpContext?.Request.Headers.Authorization.FirstOrDefault();
            const string prefix = "Bearer ";
            return authorization?.StartsWith(prefix, StringComparison.OrdinalIgnoreCase) == true
                ? authorization[prefix.Length..].Trim()
                : null;
        }
    }

    public string CorrelationId =>
        Activity.Current?.TraceId.ToString() ??
        _httpContextAccessor.HttpContext?.TraceIdentifier ??
        "sem-correlation-id";
}
