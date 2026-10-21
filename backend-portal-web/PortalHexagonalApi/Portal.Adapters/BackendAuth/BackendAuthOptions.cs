namespace Portal.Adapters.BackendAuth;

public sealed class BackendAuthOptions
{
    public const string SectionName = "BackendAuth";
    public string BaseUrl { get; set; } = string.Empty;
    public string ServiceToken { get; set; } = string.Empty;
    public string ServiceCode { get; set; } = string.Empty;
}
