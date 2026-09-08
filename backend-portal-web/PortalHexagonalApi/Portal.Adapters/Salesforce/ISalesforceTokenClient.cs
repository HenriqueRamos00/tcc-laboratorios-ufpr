namespace Portal.Adapters.Salesforce;

public interface ISalesforceTokenClient
{
    Task<SalesforceToken> GetTokenAsync(CancellationToken cancellationToken);
}
