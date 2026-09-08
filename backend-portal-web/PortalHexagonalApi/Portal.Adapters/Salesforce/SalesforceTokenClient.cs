using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Portal.Adapters.Salesforce;

public sealed class SalesforceTokenClient : ISalesforceTokenClient
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly HttpClient _httpClient;
    private readonly SalesforceOptions _options;

    public SalesforceTokenClient(HttpClient httpClient, SalesforceOptions options)
    {
        _httpClient = httpClient;
        _options = options;
    }

    public async Task<SalesforceToken> GetTokenAsync(CancellationToken cancellationToken)
    {
        ValidateOptions();

        using HttpRequestMessage request = new(HttpMethod.Post, BuildTokenUri());
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        request.Content = new FormUrlEncodedContent(BuildTokenRequestFields());

        using HttpResponseMessage response = await _httpClient.SendAsync(request, cancellationToken);
        string responseContent = await response.Content.ReadAsStringAsync(cancellationToken);

        if (!response.IsSuccessStatusCode)
        {
            throw new InvalidOperationException(
                $"Salesforce token request failed with {(int)response.StatusCode} {response.ReasonPhrase}: {responseContent}");
        }

        SalesforceTokenResponse? tokenResponse = JsonSerializer.Deserialize<SalesforceTokenResponse>(
            responseContent,
            JsonOptions);

        if (string.IsNullOrWhiteSpace(tokenResponse?.AccessToken))
            throw new InvalidOperationException("Salesforce token response did not include an access token.");

        return new SalesforceToken(
            accessToken: tokenResponse.AccessToken,
            tokenType: string.IsNullOrWhiteSpace(tokenResponse.TokenType) ? "Bearer" : tokenResponse.TokenType,
            instanceUrl: tokenResponse.InstanceUrl,
            id: tokenResponse.Id,
            issuedAt: tokenResponse.IssuedAt,
            signature: tokenResponse.Signature);
    }

    private Uri BuildTokenUri()
    {
        if (!Uri.TryCreate(_options.Url, UriKind.Absolute, out Uri? salesforceUrl))
            throw new InvalidOperationException("Salesforce:Url must be an absolute URL.");

        if (salesforceUrl.AbsolutePath.EndsWith("/services/oauth2/token", StringComparison.OrdinalIgnoreCase))
            return salesforceUrl;

        string tokenPath = string.IsNullOrWhiteSpace(_options.TokenPath)
            ? "/services/oauth2/token"
            : _options.TokenPath;

        return new Uri(salesforceUrl, tokenPath.TrimStart('/'));
    }

    private IReadOnlyDictionary<string, string> BuildTokenRequestFields()
    {
        Dictionary<string, string> fields = new()
        {
            ["grant_type"] = _options.GrantType,
            ["client_id"] = _options.ClientId,
            ["client_secret"] = _options.ClientSecret
        };

        return fields;
    }

    private void ValidateOptions()
    {
        List<string> missingSettings = [];

        if (string.IsNullOrWhiteSpace(_options.Url))
            missingSettings.Add("Salesforce:Url");

        if (string.IsNullOrWhiteSpace(_options.GrantType))
            missingSettings.Add("Salesforce:GrantType");

        if (string.IsNullOrWhiteSpace(_options.ClientId))
            missingSettings.Add("Salesforce:ClientId");

        if (string.IsNullOrWhiteSpace(_options.ClientSecret))
            missingSettings.Add("Salesforce:ClientSecret");

        if (missingSettings.Count > 0)
            throw new InvalidOperationException($"Missing Salesforce configuration: {string.Join(", ", missingSettings)}.");
    }

    private sealed class SalesforceTokenResponse
    {
        [JsonPropertyName("access_token")]
        public string? AccessToken { get; set; }

        [JsonPropertyName("token_type")]
        public string? TokenType { get; set; }

        [JsonPropertyName("instance_url")]
        public string? InstanceUrl { get; set; }

        [JsonPropertyName("id")]
        public string? Id { get; set; }

        [JsonPropertyName("issued_at")]
        public string? IssuedAt { get; set; }

        [JsonPropertyName("signature")]
        public string? Signature { get; set; }
    }
}
