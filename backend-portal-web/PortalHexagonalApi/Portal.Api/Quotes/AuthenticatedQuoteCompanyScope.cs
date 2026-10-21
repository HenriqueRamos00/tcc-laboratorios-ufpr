using Portal.Application.Quotes;

namespace Portal.Api.Quotes;

// The JWT middleware verifies these claims before the quote endpoints run.
public sealed class AuthenticatedQuoteCompanyScope(IHttpContextAccessor context) : IQuoteCompanyScope
{
    public IReadOnlyList<string> CompanyCnpjs => context.HttpContext?.User is { Identity.IsAuthenticated: true } user
        ? user.FindAll("companyCnpjs").Select(claim => claim.Value).ToArray()
        : [];
}
