namespace Portal.Adapters.Salesforce;

public sealed class SalesforceToken
{
    public string AccessToken { get; }
    public string TokenType { get; }
    public string? InstanceUrl { get; }
    public string? Id { get; }
    public string? IssuedAt { get; }
    public string? Signature { get; }

    public SalesforceToken(
        string accessToken,
        string tokenType,
        string? instanceUrl,
        string? id,
        string? issuedAt,
        string? signature)
    {
        AccessToken = accessToken;
        TokenType = tokenType;
        InstanceUrl = instanceUrl;
        Id = id;
        IssuedAt = issuedAt;
        Signature = signature;
    }
}
