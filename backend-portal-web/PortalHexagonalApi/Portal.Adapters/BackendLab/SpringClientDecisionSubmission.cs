using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;

using Portal.Adapters.Quotes;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Adapters.BackendLab;

public sealed class SpringClientDecisionSubmission(HttpClient httpClient) : IClientDecisionSubmission
{
    private const int InvoiceEmailsMaxLength = 500;

    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public async Task<ClientDecisionOutcome> SubmitAsync(
        string quoteId,
        BackendLabClientDecisionRequest request,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(request);

        if (!SpringQuoteIdentifier.IsValid(quoteId))
            return ClientDecisionOutcome.QuoteNotFound;

        using HttpRequestMessage message = CreateRequest(quoteId, request);
        HttpResponseMessage response;
        try
        {
            response = await httpClient.SendAsync(message, HttpCompletionOption.ResponseHeadersRead, cancellationToken);
        }
        catch (HttpRequestException failure)
        {
            throw new QuoteSourceUnavailableException("O registro do aceite no Spring falhou.", failure);
        }
        catch (IOException failure)
        {
            throw new QuoteSourceUnavailableException("O registro do aceite no Spring falhou.", failure);
        }

        using (response)
        {
            if (response.StatusCode == HttpStatusCode.Unauthorized)
                throw new QuoteAuthenticationRequiredException();
            if (response.StatusCode == HttpStatusCode.Forbidden)
                throw new QuoteAccessForbiddenException();
            if (response.StatusCode == HttpStatusCode.NotFound)
                return ClientDecisionOutcome.QuoteNotFound;
            if (response.StatusCode == HttpStatusCode.Conflict)
                return ClientDecisionOutcome.NotAllowed;
            if (response.StatusCode == HttpStatusCode.BadRequest || (int)response.StatusCode == 413)
            {
                string detail = await ReadRejectionAsync(response, cancellationToken);
                throw new ClientDecisionRequestRejectedException(detail);
            }

            cancellationToken.ThrowIfCancellationRequested();
            if (!response.IsSuccessStatusCode)
                throw new QuoteSourceUnavailableException("O Spring recusou o registro do aceite.");
        }

        return ClientDecisionOutcome.Recorded;
    }

    private static HttpRequestMessage CreateRequest(string quoteId, BackendLabClientDecisionRequest request)
    {
        string escapedId = Uri.EscapeDataString(quoteId);

        return request.Kind switch
        {
            ClientDecisionKind.Acceptance when request.Terms is not null =>
                AcceptanceRequest(escapedId, request.Terms),
            ClientDecisionKind.PurchaseOrder when request.PurchaseOrder is not null =>
                PurchaseOrderRequest(escapedId, request.PurchaseOrder),
            _ => throw new ClientDecisionRequestRejectedException("A forma de aceite não foi informada corretamente.")
        };
    }

    private static HttpRequestMessage AcceptanceRequest(string quoteId, AcceptanceTerms terms)
    {
        BillingAddress billing = terms.Billing;
        AcceptanceApproval approval = terms.Approval;
        string invoiceEmails = string.Join(", ", billing.InvoiceEmails);
        if (invoiceEmails.Length > InvoiceEmailsMaxLength)
        {
            throw new ClientDecisionRequestRejectedException(
                "A soma dos e-mails para nota fiscal excede o limite aceito pelo cadastro do laboratório.");
        }

        var body = new
        {
            billing = new
            {
                billing.Company,
                billing.Cnpj,
                billing.StateRegistration,
                billing.Street,
                billing.City,
                billing.State,
                billing.District,
                billing.PostalCode,
                billing.Contact,
                billing.Phone,
                fax = (string?)null,
                invoiceEmails
            },
            approval = new
            {
                approval.RequesterName,
                approval.ResponsibleName,
                approval.JobTitle,
                approval.Department,
                approval.Date,
                approval.AllowsResultsByEmail,
                approval.ResultsEmail
            }
        };

        return new HttpRequestMessage(HttpMethod.Post, $"api/client/quotes/{quoteId}/acceptance")
        {
            Content = JsonContent.Create(body, options: JsonOptions)
        };
    }

    private static HttpRequestMessage PurchaseOrderRequest(string quoteId, PurchaseOrderFile purchaseOrder)
    {
        string contentType = Path.GetExtension(purchaseOrder.FileName).ToLowerInvariant() switch
        {
            ".pdf" => "application/pdf",
            ".doc" => "application/msword",
            ".docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            _ => throw new ClientDecisionRequestRejectedException("A ordem de compra precisa ser um arquivo PDF, DOC ou DOCX.")
        };

        MultipartFormDataContent form = new();
        ByteArrayContent document = new(purchaseOrder.Content);
        document.Headers.ContentType = new MediaTypeHeaderValue(contentType);
        form.Add(document, "document", purchaseOrder.FileName);

        return new HttpRequestMessage(HttpMethod.Post, $"api/client/quotes/{quoteId}/purchase-order")
        {
            Content = form
        };
    }

    private static async Task<string> ReadRejectionAsync(
        HttpResponseMessage response,
        CancellationToken cancellationToken)
    {
        try
        {
            using JsonDocument body = await JsonDocument.ParseAsync(
                await response.Content.ReadAsStreamAsync(cancellationToken), cancellationToken: cancellationToken);
            if (body.RootElement.TryGetProperty("message", out JsonElement message)
                && message.ValueKind == JsonValueKind.String
                && !string.IsNullOrWhiteSpace(message.GetString()))
                return message.GetString()!;
        }
        catch (Exception failure) when (failure is JsonException or IOException)
        {
            // A resposta de erro pode não conter o envelope JSON usado pelo Spring.
        }

        return "O Spring não aceitou os dados enviados para o aceite.";
    }
}
