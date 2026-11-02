using System.Net;
using System.Text.Json;

using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Adapters.Quotes;

public sealed class SpringQuoteRepository(HttpClient httpClient) : IQuoteRepository
{
    public async Task<IReadOnlyList<Quote>> GetQuotesAsync(CancellationToken cancellationToken)
    {
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync("api/client/quotes", operation.Token, cancellationToken);
        EnsureSuccess(response, operation.Token);
        try
        {
            await using Stream content = await response.Content.ReadAsStreamAsync(operation.Token);
            return await SpringQuoteContract.ReadQuotesAsync(content, operation.Token);
        }
        catch (Exception failure) when (failure is JsonException or IOException or HttpRequestException)
        {
            throw new QuoteSourceUnavailableException("A resposta de orçamentos do Spring é inválida.", failure);
        }
        catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
        {
            throw new QuoteSourceUnavailableException("A consulta de orçamentos do Spring excedeu o tempo limite.", failure);
        }
    }

    public async Task<Quote?> GetQuoteByIdAsync(string id, CancellationToken cancellationToken)
    {
        if (!SpringQuoteIdentifier.IsValid(id)) return null;
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpResponseMessage response = await SendAsync(
            $"api/client/quotes/{Uri.EscapeDataString(id)}", operation.Token, cancellationToken);
        if (response.StatusCode == HttpStatusCode.NotFound) return null;
        EnsureSuccess(response, operation.Token);
        try
        {
            await using Stream content = await response.Content.ReadAsStreamAsync(operation.Token);
            return await SpringQuoteContract.ReadQuoteAsync(content, operation.Token);
        }
        catch (Exception failure) when (failure is JsonException or IOException or HttpRequestException)
        {
            throw new QuoteSourceUnavailableException("A resposta de orçamento do Spring é inválida.", failure);
        }
        catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
        {
            throw new QuoteSourceUnavailableException("A consulta de orçamento do Spring excedeu o tempo limite.", failure);
        }
    }

    private CancellationTokenSource CreateOperationTimeout(CancellationToken cancellationToken)
    {
        CancellationTokenSource operation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        if (httpClient.Timeout != Timeout.InfiniteTimeSpan)
            operation.CancelAfter(httpClient.Timeout);
        return operation;
    }

    private async Task<HttpResponseMessage> SendAsync(
        string path,
        CancellationToken operationToken,
        CancellationToken consumerToken)
    {
        using HttpRequestMessage request = new(HttpMethod.Get, path);
        try
        {
            return await httpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, operationToken);
        }
        catch (OperationCanceledException failure) when (operationToken.IsCancellationRequested
            && !consumerToken.IsCancellationRequested)
        {
            throw new QuoteSourceUnavailableException("A consulta de orçamentos do Spring excedeu o tempo limite.", failure);
        }
        catch (HttpRequestException failure)
        {
            throw new QuoteSourceUnavailableException("A consulta de orçamentos do Spring falhou.", failure);
        }
        catch (IOException failure)
        {
            throw new QuoteSourceUnavailableException("A consulta de orçamentos do Spring falhou.", failure);
        }
    }

    private static bool IsOwnTimeout(CancellationTokenSource operation, CancellationToken consumerToken) =>
        operation.IsCancellationRequested && !consumerToken.IsCancellationRequested;

    private static void EnsureSuccess(HttpResponseMessage response, CancellationToken cancellationToken)
    {
        if (response.StatusCode == HttpStatusCode.Unauthorized)
            throw new QuoteAuthenticationRequiredException();
        if (response.StatusCode == HttpStatusCode.Forbidden)
            throw new QuoteAccessForbiddenException();

        cancellationToken.ThrowIfCancellationRequested();
        if (response.IsSuccessStatusCode) return;
        throw new QuoteSourceUnavailableException("A consulta de orçamentos do Spring falhou.");
    }
}
