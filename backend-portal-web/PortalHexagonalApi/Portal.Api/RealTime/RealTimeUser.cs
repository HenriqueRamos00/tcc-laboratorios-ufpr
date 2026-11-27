using System.Security.Claims;

namespace Portal.Api.RealTime;

public static class RealTimeUser
{
    public const string UserClaim = "sub";

    public static string? KeyOf(ClaimsPrincipal? user)
    {
        if (user?.Identity is not { IsAuthenticated: true }) return null;

        string? key = user.FindFirst(UserClaim)?.Value;

        return string.IsNullOrWhiteSpace(key) ? null : key;
    }
}
