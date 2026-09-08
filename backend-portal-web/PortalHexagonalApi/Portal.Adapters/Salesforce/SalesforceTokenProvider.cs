namespace Portal.Adapters.Salesforce;

public sealed class SalesforceTokenProvider : ISalesforceTokenProvider
{
    private readonly ISalesforceTokenClient _salesforceTokenClient;
    private readonly SemaphoreSlim _refreshLock = new(1, 1);
    private SalesforceToken? _cachedToken;

    public SalesforceTokenProvider(ISalesforceTokenClient salesforceTokenClient)
    {
        _salesforceTokenClient = salesforceTokenClient;
    }

    public async Task<SalesforceToken> GetTokenAsync(CancellationToken cancellationToken)
    {
        if (_cachedToken is not null)
            return _cachedToken;

        await _refreshLock.WaitAsync(cancellationToken);
        try
        {
            _cachedToken ??= await _salesforceTokenClient.GetTokenAsync(cancellationToken);
            return _cachedToken;
        }
        finally
        {
            _refreshLock.Release();
        }
    }

    public async Task<SalesforceToken> RefreshTokenAsync(CancellationToken cancellationToken)
    {
        await _refreshLock.WaitAsync(cancellationToken);
        try
        {
            _cachedToken = await _salesforceTokenClient.GetTokenAsync(cancellationToken);
            return _cachedToken;
        }
        finally
        {
            _refreshLock.Release();
        }
    }
}
