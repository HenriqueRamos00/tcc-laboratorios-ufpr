using System.Text.Json;
using System.Text.Json.Serialization;

using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

internal static class SpringQuoteContract
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNameCaseInsensitive = false,
        NumberHandling = JsonNumberHandling.Strict
    };

    public static async Task<IReadOnlyList<Quote>> ReadQuotesAsync(
        Stream content,
        CancellationToken cancellationToken)
    {
        List<SpringQuoteTransport?>? values = await JsonSerializer.DeserializeAsync<List<SpringQuoteTransport?>>(
            content, SerializerOptions, cancellationToken);
        if (values is null)
            throw new JsonException("A lista de orçamentos do Spring não pode ser nula.");

        return values.Select(value => (value ?? throw new JsonException(
                "A lista de orçamentos do Spring não pode conter itens nulos."))
            .ToQuote()).ToArray();
    }

    public static async Task<Quote> ReadQuoteAsync(Stream content, CancellationToken cancellationToken)
    {
        SpringQuoteTransport? value = await JsonSerializer.DeserializeAsync<SpringQuoteTransport>(
            content, SerializerOptions, cancellationToken);
        return (value ?? throw new JsonException("O orçamento do Spring não pode ser nulo.")).ToQuote();
    }

    private sealed class SpringQuoteTransport
    {
        [JsonRequired, JsonPropertyName("id")]
        public string? Id { get; init; }

        [JsonRequired, JsonPropertyName("code")]
        public string? Code { get; init; }

        [JsonRequired, JsonPropertyName("name")]
        public string? Name { get; init; }

        [JsonRequired, JsonPropertyName("status")]
        public string? Status { get; init; }

        [JsonRequired, JsonPropertyName("stage")]
        public string? Stage { get; init; }

        [JsonRequired, JsonPropertyName("description")]
        public string? Description { get; init; }

        [JsonRequired, JsonPropertyName("companyName")]
        public string? CompanyName { get; init; }

        [JsonRequired, JsonPropertyName("externalContactName")]
        public string? ExternalContactName { get; init; }

        [JsonRequired, JsonPropertyName("externalContactEmail")]
        public string? ExternalContactEmail { get; init; }

        [JsonRequired, JsonPropertyName("totalPrice")]
        public decimal? TotalPrice { get; init; }

        [JsonRequired, JsonPropertyName("createdDate")]
        public DateTimeOffset? CreatedDate { get; init; }

        public Quote ToQuote()
        {
            string id = RequiredText(Id, "id", identifier: true);
            string stage = RequiredText(Stage, "stage", identifier: true);
            return new Quote(
                id,
                string.Empty,
                RequiredText(Code, "code"),
                RequiredText(Name, "name"),
                RequiredText(Status, "status"),
                stage,
                RequiredText(Description, "description"),
                RequiredText(CompanyName, "companyName"),
                ExternalContactName ?? string.Empty,
                ExternalContactEmail ?? string.Empty,
                TotalPrice ?? throw new JsonException("Campo totalPrice ausente ou inválido."),
                CreatedDate);
        }
    }

    private static string RequiredText(string? value, string name, bool identifier = false)
    {
        if (value is null || (identifier && string.IsNullOrWhiteSpace(value)))
            throw new JsonException($"Campo {name} ausente ou inválido.");
        return value;
    }
}
