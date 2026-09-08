namespace Portal.Adapters.Salesforce;

public interface ISalesforceTokenProvider
{
    Task<SalesforceToken> GetTokenAsync(CancellationToken cancellationToken);
    Task<SalesforceToken> RefreshTokenAsync(CancellationToken cancellationToken);
}
