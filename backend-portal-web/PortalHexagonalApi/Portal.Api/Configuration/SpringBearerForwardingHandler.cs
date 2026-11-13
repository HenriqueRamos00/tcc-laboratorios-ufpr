using System.Net.Http.Headers;

using Portal.Application.Quotes.Exceptions;

namespace Portal.Api.Configuration;

/// <summary>Encaminha apenas o Bearer da requisição atual ao Spring.</summary>
public sealed class SpringBearerForwardingHandler(IHttpContextAccessor contextAccessor) : DelegatingHandler
{
    protected override Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request, CancellationToken cancellationToken)
    {
        string? authorization = contextAccessor.HttpContext?.Request.Headers["Authorization"].ToString();
        if (!SpringBearerCredential.IsValid(authorization))
            throw new QuoteAuthenticationRequiredException();

        request.Headers.TryAddWithoutValidation("Authorization", authorization);
        return base.SendAsync(request, cancellationToken);
    }
}

public static class SpringBearerCredential
{
    public static bool IsValid(string? value) =>
        !string.IsNullOrWhiteSpace(value)
        && AuthenticationHeaderValue.TryParse(value, out var parsed)
        && string.Equals(parsed.Scheme, "Bearer", StringComparison.OrdinalIgnoreCase)
        && !string.IsNullOrWhiteSpace(parsed.Parameter)
        && !parsed.Parameter.Any(char.IsWhiteSpace);
}
