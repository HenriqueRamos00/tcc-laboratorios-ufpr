using System.Net;

using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;

namespace Portal.Adapters.Quotes;

public sealed class SpringQuoteDocumentRepository(HttpClient httpClient) : IQuoteDocumentRepository
{
    public async Task<byte[]?> GetDocumentAsync(string quoteId, CancellationToken cancellationToken)
    {
        if (!SpringQuoteIdentifier.IsValid(quoteId)) return null;
        using CancellationTokenSource operation = CreateOperationTimeout(cancellationToken);
        using HttpRequestMessage request = new(
            HttpMethod.Get, $"api/client/quotes/{Uri.EscapeDataString(quoteId)}/document");
        HttpResponseMessage response;
        try
        {
            response = await httpClient.SendAsync(
                request, HttpCompletionOption.ResponseHeadersRead, operation.Token);
        }
        catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
        {
            throw new QuoteSourceUnavailableException("A consulta do documento do Spring excedeu o tempo limite.", failure);
        }
        catch (HttpRequestException failure)
        {
            throw new QuoteSourceUnavailableException("A consulta do documento do Spring falhou.", failure);
        }
        catch (IOException failure)
        {
            throw new QuoteSourceUnavailableException("A consulta do documento do Spring falhou.", failure);
        }

        using (response)
        {
            if (response.StatusCode == HttpStatusCode.NotFound) return null;
            if (response.StatusCode == HttpStatusCode.Unauthorized)
                throw new QuoteAuthenticationRequiredException();
            if (response.StatusCode == HttpStatusCode.Forbidden)
                throw new QuoteAccessForbiddenException();
            if (!response.IsSuccessStatusCode)
                throw new QuoteDocumentUnavailableException("A consulta do documento do Spring falhou.");
            if (!string.Equals(response.Content.Headers.ContentType?.MediaType, "application/pdf",
                    StringComparison.OrdinalIgnoreCase))
                throw new QuoteDocumentUnavailableException("O documento retornado pelo Spring não é PDF.");

            try
            {
                byte[] document = await response.Content.ReadAsByteArrayAsync(operation.Token);
                if (document.Length == 0)
                    throw new QuoteDocumentUnavailableException("O documento retornado pelo Spring está vazio.");
                return document;
            }
            catch (OperationCanceledException failure) when (IsOwnTimeout(operation, cancellationToken))
            {
                throw new QuoteSourceUnavailableException("A leitura do documento do Spring excedeu o tempo limite.", failure);
            }
            catch (Exception failure) when (failure is HttpRequestException or IOException)
            {
                throw new QuoteDocumentUnavailableException("Não foi possível ler o documento do Spring.", failure);
            }
        }
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
}
