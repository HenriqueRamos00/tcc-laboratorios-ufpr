using System.Security.Claims;

using Portal.Application.Visibility;

namespace Portal.Api.LabResults;

public static class VisibilityScopeResolution
{
    public const string TenantClaimName = "inquilino";

    public static VisibilityScope? Resolve(ClaimsPrincipal? user, LabResultsOptions options)
    {
        ArgumentNullException.ThrowIfNull(options);

        if (options.Scope == LabResultsOptions.AllScope) return VisibilityScope.All;

        string? fromClaim = user?.FindFirst(TenantClaimName)?.Value;

        if (!string.IsNullOrWhiteSpace(fromClaim)) return VisibilityScope.OfCompany(fromClaim);

        return string.IsNullOrWhiteSpace(options.DevelopmentTenant)
            ? null
            : VisibilityScope.OfCompany(options.DevelopmentTenant);
    }
}
