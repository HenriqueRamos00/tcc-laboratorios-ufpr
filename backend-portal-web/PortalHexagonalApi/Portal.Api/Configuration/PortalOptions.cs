namespace Portal.Api.Configuration;

public sealed class PortalOptions
{
    public const string SectionName = "Portal";
    public const string BackendLabSource = "backend-lab";
    public const string LactecSource = "lactec";

    public string? DataSource { get; init; }
}
