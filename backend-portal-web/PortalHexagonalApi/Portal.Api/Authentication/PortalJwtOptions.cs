namespace Portal.Api.Authentication;

public sealed class PortalJwtOptions
{
    public const string SectionName = "PortalJwt";
    public string Issuer { get; set; } = string.Empty;
    public string Audience { get; set; } = string.Empty;
    public string SigningKey { get; set; } = string.Empty;
    public int ExpirationSeconds { get; set; } = 900;

    public static bool HasValidSigningKey(PortalJwtOptions options)
    {
        if (string.IsNullOrWhiteSpace(options.SigningKey)) return false;
        try { return Convert.FromBase64String(options.SigningKey).Length >= 32; }
        catch (FormatException) { return false; }
    }
}
