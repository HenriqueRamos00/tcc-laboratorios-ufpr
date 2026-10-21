using System.Globalization;
using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Portal.Application.Quotes.Exceptions;
using Portal.Adapters.Salesforce;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

public sealed class SalesforceQuoteRepository : IQuoteRepository
{
    private const string QuoteFields = "Id, Name, Status, OpportunityId, Opportunity.Codigo_da_Oportunidade__c, Opportunity.StageName, Opportunity.Description, Opportunity.Account.Name, Opportunity.Account.CNPJ__c, Opportunity.Owner.Name, Email_proprietario_oportunidade__c, Valor_total_DRE__c, CreatedDate";

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    private readonly HttpClient _httpClient;
    private readonly ISalesforceTokenProvider _tokenProvider;
    private readonly SalesforceOptions _options;
    private readonly IQuoteCompanyScope _companyScope;

    public SalesforceQuoteRepository(
        HttpClient httpClient,
        ISalesforceTokenProvider tokenProvider,
        SalesforceOptions options,
        IQuoteCompanyScope companyScope)
    {
        _httpClient = httpClient;
        _tokenProvider = tokenProvider;
        _options = options;
        _companyScope = companyScope;
    }

    public async Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        string? companyFilter = BuildCompanyFilter();
        if (companyFilter is null) return [];

        string query = $"SELECT {QuoteFields} FROM Quote WHERE {companyFilter} ORDER BY CreatedDate DESC LIMIT 5";
        SalesforceQueryResponse response = await QuerySalesforceAsync(query, cancellationToken);
        return response.Records.Select(MapQuote).ToList();
    }

    public async Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
    {
        cancellationToken.ThrowIfCancellationRequested();
        string? companyFilter = BuildCompanyFilter();
        if (companyFilter is null) return null;

        string escapedId = EscapeSoqlString(id);
        string query = $"SELECT {QuoteFields} FROM Quote WHERE Id = '{escapedId}' AND {companyFilter} LIMIT 1";

        SalesforceQueryResponse response = await QuerySalesforceAsync(query, cancellationToken);
        SalesforceQuoteRecord? record = response.Records.FirstOrDefault();

        return record is null ? null : MapQuote(record);
    }

    private string? BuildCompanyFilter()
    {
        IReadOnlyList<string> cnpjs = _companyScope.CompanyCnpjs;
        if (cnpjs.Count == 0 || cnpjs.Any(cnpj => cnpj is null
                || !Regex.IsMatch(cnpj, @"\A[A-Z0-9]{12}[0-9]{2}\z", RegexOptions.CultureInvariant)))
            return null;

        string[] values = cnpjs.Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal)
            .Select(cnpj => $"'{EscapeSoqlString(cnpj)}'").ToArray();
        return values.Length == 1
            ? $"Opportunity.Account.CNPJ__c = {values[0]}"
            : $"Opportunity.Account.CNPJ__c IN ({string.Join(", ", values)})";
    }

    private async Task<SalesforceQueryResponse> QuerySalesforceAsync(string query, CancellationToken cancellationToken)
    {
        try
        {
            SalesforceToken token = await _tokenProvider.GetTokenAsync(cancellationToken);
            HttpResponseMessage response = await SendQueryAsync(query, token, cancellationToken);
            try
            {
                if (IsAuthFailure(response.StatusCode))
                {
                    response.Dispose();
                    token = await _tokenProvider.RefreshTokenAsync(cancellationToken);
                    response = await SendQueryAsync(query, token, cancellationToken);
                }
                if (!response.IsSuccessStatusCode)
                    throw new QuoteSourceUnavailableException("A consulta de orçamentos do Salesforce falhou.");

                await using Stream responseStream = await response.Content.ReadAsStreamAsync(cancellationToken);
                SalesforceQueryResponse? result = await JsonSerializer.DeserializeAsync<SalesforceQueryResponse>(
                    responseStream, JsonOptions, cancellationToken);
                return result is { Records: not null } ? result
                    : throw new QuoteSourceUnavailableException("A resposta de orçamentos do Salesforce é inválida.");
            }
            finally
            {
                response.Dispose();
            }
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            throw new QuoteSourceUnavailableException("A consulta de orçamentos do Salesforce excedeu o tempo de resposta.");
        }
        catch (Exception failure) when (failure is HttpRequestException or JsonException or InvalidOperationException or NotSupportedException)
        {
            throw new QuoteSourceUnavailableException("Não foi possível consultar os orçamentos do Salesforce.");
        }
    }

    private async Task<HttpResponseMessage> SendQueryAsync(
        string query,
        SalesforceToken token,
        CancellationToken cancellationToken)
    {
        using HttpRequestMessage request = new(HttpMethod.Get, BuildQueryUri(token, query));
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        request.Headers.Authorization = new AuthenticationHeaderValue(
            string.IsNullOrWhiteSpace(token.TokenType) ? "Bearer" : token.TokenType,
            token.AccessToken);

        return await _httpClient.SendAsync(request, cancellationToken);
    }

    private Uri BuildQueryUri(SalesforceToken token, string query)
    {
        string baseUrl = string.IsNullOrWhiteSpace(token.InstanceUrl) ? _options.Url : token.InstanceUrl;

        if (!Uri.TryCreate(baseUrl, UriKind.Absolute, out Uri? salesforceUrl))
            throw new InvalidOperationException("Salesforce base URL must be an absolute URL.");

        string apiVersion = string.IsNullOrWhiteSpace(_options.ApiVersion) ? "v60.0" : _options.ApiVersion;
        Uri queryUri = new(salesforceUrl, $"services/data/{apiVersion}/query/");
        UriBuilder uriBuilder = new(queryUri)
        {
            Query = $"q={Uri.EscapeDataString(query)}"
        };

        return uriBuilder.Uri;
    }

    private static bool IsAuthFailure(HttpStatusCode statusCode)
    {
        return statusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden;
    }

    private static Quote MapQuote(SalesforceQuoteRecord record)
    {
        return new Quote(
            id: record.Id ?? string.Empty,
            opportunityId: record.OpportunityId ?? string.Empty,
            code: record.Opportunity?.Code ?? string.Empty,
            name: record.Name ?? string.Empty,
            status: record.Status ?? string.Empty,
            stage: record.Opportunity?.StageName ?? string.Empty,
            description: record.Opportunity?.Description ?? string.Empty,
            companyName: record.Opportunity?.Account?.Name ?? string.Empty,
            externalContactName: record.Opportunity?.Owner?.Name ?? string.Empty,
            externalContactEmail: record.OwnerEmail ?? string.Empty,
            totalPrice: record.TotalPrice ?? 0m,
            createdDate: ParseSalesforceDate(record.CreatedDate));
    }

    private static DateTimeOffset? ParseSalesforceDate(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return null;

        return DateTimeOffset.TryParse(value, CultureInfo.InvariantCulture, DateTimeStyles.None, out DateTimeOffset parsed)
            ? parsed
            : null;
    }

    private static string EscapeSoqlString(string value)
    {
        return value.Replace("\\", "\\\\").Replace("'", "\\'");
    }

    private sealed class SalesforceQueryResponse
    {
        [JsonPropertyName("records")]
        public List<SalesforceQuoteRecord> Records { get; set; } = [];
    }

    private sealed class SalesforceQuoteRecord
    {
        [JsonPropertyName("Id")]
        public string? Id { get; set; }

        [JsonPropertyName("Name")]
        public string? Name { get; set; }

        [JsonPropertyName("Status")]
        public string? Status { get; set; }

        [JsonPropertyName("OpportunityId")]
        public string? OpportunityId { get; set; }

        [JsonPropertyName("Email_proprietario_oportunidade__c")]
        public string? OwnerEmail { get; set; }

        [JsonPropertyName("Valor_total_DRE__c")]
        public decimal? TotalPrice { get; set; }

        [JsonPropertyName("CreatedDate")]
        public string? CreatedDate { get; set; }

        [JsonPropertyName("Opportunity")]
        public SalesforceOpportunityRecord? Opportunity { get; set; }
    }

    private sealed class SalesforceOpportunityRecord
    {
        [JsonPropertyName("Codigo_da_Oportunidade__c")]
        public string? Code { get; set; }

        [JsonPropertyName("Description")]
        public string? Description { get; set; }

        [JsonPropertyName("StageName")]
        public string? StageName { get; set; }

        [JsonPropertyName("Account")]
        public SalesforceAccountRecord? Account { get; set; }

        [JsonPropertyName("Owner")]
        public SalesforceOwnerRecord? Owner { get; set; }
    }

    private sealed class SalesforceAccountRecord
    {
        [JsonPropertyName("Name")]
        public string? Name { get; set; }
    }

    private sealed class SalesforceOwnerRecord
    {
        [JsonPropertyName("Name")]
        public string? Name { get; set; }
    }
}
