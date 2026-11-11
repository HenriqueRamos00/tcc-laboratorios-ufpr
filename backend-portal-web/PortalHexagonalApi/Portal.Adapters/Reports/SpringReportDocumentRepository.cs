using System.Net;
using System.Text.Json;
using System.Text.Json.Serialization;

using Portal.Application.Reports;
using Portal.Application.Reports.Exceptions;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Reports;

namespace Portal.Adapters.Reports;

public sealed class SpringReportDocumentRepository(HttpClient httpClient) : IReportDocumentRepository
{
    private static readonly JsonSerializerOptions SerializerOptions = new()
    {
        PropertyNameCaseInsensitive = false,
        NumberHandling = JsonNumberHandling.Strict
    };

    public async Task<IReadOnlyList<PublishedReport>> GetPublishedAsync(CancellationToken cancellationToken)
    {
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, "api/client/reports"),
            operation,
            cancellationToken);

        EnsureSuccess(response, cancellationToken);
        return await ReadResponseAsync<List<SpringPublishedReport?>, IReadOnlyList<PublishedReport>>(
            response, operation, cancellationToken, MapReports);
    }

    public async Task<QuotePublishedReports> GetPublishedOfQuoteAsync(
        string quoteId,
        CancellationToken cancellationToken)
    {
        string path = $"api/client/quotes/{Uri.EscapeDataString(quoteId)}/reports";
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, path),
            operation,
            cancellationToken);

        EnsureSuccess(response, cancellationToken);
        return await ReadResponseAsync<SpringQuoteReports, QuotePublishedReports>(
            response, operation, cancellationToken, value => value.ToQuotePublishedReports());
    }

    public async Task<PublishedReportFile?> GetFileAsync(
        string reportId,
        CancellationToken cancellationToken)
    {
        string path = $"api/client/reports/{Uri.EscapeDataString(reportId)}/file";
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            new HttpRequestMessage(HttpMethod.Get, path),
            operation,
            cancellationToken);

        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        EnsureSuccess(response, cancellationToken);

        if (!string.Equals(response.Content.Headers.ContentType?.MediaType, "application/pdf", StringComparison.OrdinalIgnoreCase))
            throw new ReportSourceUnavailableException("A resposta de arquivo de relatório do Spring não é um PDF.");

        try
        {
            await using Stream stream = await response.Content.ReadAsStreamAsync(operation.Token);
            using MemoryStream content = new();
            await stream.CopyToAsync(content, operation.Token);
            byte[] bytes = content.ToArray();
            if (bytes.Length == 0)
                throw new ReportSourceUnavailableException("O PDF de relatório publicado está vazio.");

            return new PublishedReportFile(bytes);
        }
        catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
        {
            throw new ReportSourceUnavailableException("A leitura do PDF de relatório do Spring excedeu o tempo limite.", failure);
        }
        catch (Exception failure) when (failure is IOException or HttpRequestException)
        {
            throw new ReportSourceUnavailableException("A leitura do PDF de relatório do Spring falhou.", failure);
        }
    }

    private async Task<HttpResponseMessage> SendAsync(
        HttpRequestMessage request,
        CancellationTokenSource operation,
        CancellationToken cancellationToken)
    {
        using (request)
        {
            try
            {
                return await httpClient.SendAsync(
                    request,
                    HttpCompletionOption.ResponseHeadersRead,
                    operation.Token);
            }
            catch (QuoteAuthenticationRequiredException failure)
            {
                throw new ReportAuthenticationRequiredException(failure);
            }
            catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
            {
                throw new ReportSourceUnavailableException("A consulta de relatórios no Spring excedeu o tempo limite.", failure);
            }
            catch (Exception failure) when (failure is OperationCanceledException or HttpRequestException or IOException)
            {
                if (failure is OperationCanceledException && cancellationToken.IsCancellationRequested)
                    throw;

                throw new ReportSourceUnavailableException("A consulta de relatórios no Spring falhou.", failure);
            }
        }
    }

    private static async Task<TResult> ReadResponseAsync<TContract, TResult>(
        HttpResponseMessage response,
        CancellationTokenSource operation,
        CancellationToken cancellationToken,
        Func<TContract, TResult> convert)
    {
        try
        {
            await using Stream content = await response.Content.ReadAsStreamAsync(operation.Token);
            TContract? value = await JsonSerializer.DeserializeAsync<TContract>(
                content,
                SerializerOptions,
                operation.Token);

            if (value is null)
                throw new JsonException("A resposta de relatórios do Spring não pode ser nula.");

            return convert(value);
        }
        catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
        {
            throw new ReportSourceUnavailableException("A leitura dos relatórios do Spring excedeu o tempo limite.", failure);
        }
        catch (Exception failure) when (failure is JsonException or IOException or HttpRequestException)
        {
            throw new ReportSourceUnavailableException("A resposta de relatórios do Spring é inválida.", failure);
        }
    }

    private static IReadOnlyList<PublishedReport> MapReports(List<SpringPublishedReport?>? values)
    {
        if (values is null)
            throw new JsonException("A lista de relatórios do Spring não pode ser nula.");

        return values.Select(value => (value ?? throw new JsonException(
                "A lista de relatórios do Spring não pode conter itens nulos."))
            .ToPublishedReport()).ToArray();
    }

    private static void EnsureSuccess(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        if (response.StatusCode == HttpStatusCode.Unauthorized)
            throw new ReportAuthenticationRequiredException();
        if (response.StatusCode == HttpStatusCode.Forbidden)
            throw new ReportAccessForbiddenException();

        cancellationToken.ThrowIfCancellationRequested();
        if (response.IsSuccessStatusCode) return;

        throw new ReportSourceUnavailableException("A fonte de relatórios do Spring não respondeu à consulta.");
    }

    private CancellationTokenSource CreateOperationTimeout(CancellationToken cancellationToken)
    {
        CancellationTokenSource operation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        if (httpClient.Timeout != Timeout.InfiniteTimeSpan)
            operation.CancelAfter(httpClient.Timeout);
        return operation;
    }

    private static bool IsOwnTimeout(CancellationTokenSource operation, CancellationToken consumerToken) =>
        operation.IsCancellationRequested && !consumerToken.IsCancellationRequested;

    private sealed class SpringPublishedReport
    {
        [JsonRequired, JsonPropertyName("id")]
        public string? Id { get; init; }

        [JsonRequired, JsonPropertyName("code")]
        public string? Code { get; init; }

        [JsonRequired, JsonPropertyName("protocol")]
        public string? Protocol { get; init; }

        [JsonRequired, JsonPropertyName("sample")]
        public string? Sample { get; init; }

        [JsonRequired, JsonPropertyName("laboratory")]
        public string? Laboratory { get; init; }

        [JsonRequired, JsonPropertyName("publishedOn")]
        public DateTimeOffset? PublishedOn { get; init; }

        public PublishedReport ToPublishedReport()
        {
            DateTimeOffset publishedAt = PublishedOn
                ?? throw new JsonException("Campo publishedOn ausente ou inválido.");

            return new PublishedReport(
                RequiredText(Id, "id"),
                RequiredText(Code, "code"),
                RequiredText(Protocol, "protocol"),
                RequiredText(Sample, "sample"),
                RequiredText(Laboratory, "laboratory"),
                publishedAt,
                publishedAt);
        }
    }

    private sealed class SpringQuoteReports
    {
        [JsonRequired, JsonPropertyName("protocol")]
        public string? Protocol { get; init; }

        [JsonRequired, JsonPropertyName("testType")]
        public string? TestType { get; init; }

        [JsonRequired, JsonPropertyName("summary")]
        public string? Summary { get; init; }

        [JsonRequired, JsonPropertyName("reports")]
        public List<SpringPublishedReport?>? Reports { get; init; }

        public QuotePublishedReports ToQuotePublishedReports()
        {
            IReadOnlyList<PublishedReport> reports = MapReports(Reports);
            string testType = reports.Count == 0
                ? TestType ?? throw new JsonException("Campo testType ausente ou inválido.")
                : RequiredText(TestType, "testType");

            return new QuotePublishedReports(
                RequiredText(Protocol, "protocol"), testType, RequiredText(Summary, "summary"), reports);
        }
    }

    private static string RequiredText(string? value, string name)
    {
        if (string.IsNullOrWhiteSpace(value))
            throw new JsonException($"Campo {name} ausente ou inválido.");
        return value;
    }
}
