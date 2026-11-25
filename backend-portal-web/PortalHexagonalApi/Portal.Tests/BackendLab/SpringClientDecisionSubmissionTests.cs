using System.Net;
using System.Text;
using System.Text.Json;

using Microsoft.AspNetCore.Http;

using Portal.Adapters.BackendLab;
using Portal.Api.Configuration;
using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.BackendLab;

public sealed class SpringClientDecisionSubmissionTests
{
    [Fact]
    public async Task EnviaOTermoComOsCamposEsperadosPeloSpringEEncaminhaOBearer()
    {
        string? sentPath = null;
        string? sentAuthorization = null;
        string? sentBody = null;
        ResponseHandler server = new(async (request, _) =>
        {
            sentPath = request.RequestUri?.ToString();
            sentAuthorization = request.Headers.Authorization?.ToString();
            sentBody = await request.Content!.ReadAsStringAsync();
            return new HttpResponseMessage(HttpStatusCode.OK);
        });
        using HttpClient client = Client(server, "token-client");
        SpringClientDecisionSubmission submission = new(client);

        ClientDecisionOutcome result = await submission.SubmitAsync(
            "42", new BackendLabClientDecisionRequest(ClientDecisionKind.Acceptance, Terms()), CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result);
        Assert.Equal("https://spring.test/api/client/quotes/42/acceptance", sentPath);
        Assert.Equal("Bearer token-client", sentAuthorization);
        using JsonDocument body = JsonDocument.Parse(sentBody!);
        JsonElement billing = body.RootElement.GetProperty("billing");
        Assert.Equal("EMPRESA TESTE", billing.GetProperty("company").GetString());
        Assert.Equal("financeiro@empresa.test, fiscal@empresa.test", billing.GetProperty("invoiceEmails").GetString());
        Assert.Equal(JsonValueKind.Null, billing.GetProperty("fax").ValueKind);
        JsonElement approval = body.RootElement.GetProperty("approval");
        Assert.Equal("2026-11-20", approval.GetProperty("date").GetString());
        Assert.True(approval.GetProperty("allowsResultsByEmail").GetBoolean());
    }

    [Theory]
    [InlineData("ordem.pdf", "application/pdf")]
    [InlineData("ordem.doc", "application/msword")]
    [InlineData("ordem.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")]
    public async Task EnviaOrdemDeCompraComoDocumentoMultipart(string name, string expectedContentType)
    {
        string? sentPath = null;
        string? sentAuthorization = null;
        string? fieldName = null;
        string? fileName = null;
        string? contentType = null;
        byte[]? fileBytes = null;
        ResponseHandler server = new(async (request, _) =>
        {
            sentPath = request.RequestUri?.ToString();
            sentAuthorization = request.Headers.Authorization?.ToString();
            MultipartFormDataContent form = Assert.IsType<MultipartFormDataContent>(request.Content);
            HttpContent document = Assert.Single(form);
            fieldName = document.Headers.ContentDisposition?.Name?.Trim('"');
            fileName = document.Headers.ContentDisposition?.FileName?.Trim('"');
            contentType = document.Headers.ContentType?.MediaType;
            fileBytes = await document.ReadAsByteArrayAsync();
            return new HttpResponseMessage(HttpStatusCode.OK);
        });
        using HttpClient client = Client(server, "token-client");
        SpringClientDecisionSubmission submission = new(client);
        byte[] content = FileContent(name);

        ClientDecisionOutcome result = await submission.SubmitAsync(
            "42",
            new BackendLabClientDecisionRequest(
                ClientDecisionKind.PurchaseOrder,
                PurchaseOrder: new PurchaseOrderFile(name, content)),
            CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result);
        Assert.Equal("https://spring.test/api/client/quotes/42/purchase-order", sentPath);
        Assert.Equal("Bearer token-client", sentAuthorization);
        Assert.Equal(name, fileName);
        Assert.Equal("document", fieldName);
        Assert.Equal(expectedContentType, contentType);
        Assert.Equal(content, fileBytes);
    }

    [Fact]
    public async Task EnviaRecusaComoJsonComJustificativaEEncaminhaOBearer()
    {
        string? sentPath = null;
        string? sentAuthorization = null;
        string? sentBody = null;
        ResponseHandler server = new(async (request, _) =>
        {
            sentPath = request.RequestUri?.ToString();
            sentAuthorization = request.Headers.Authorization?.ToString();
            sentBody = await request.Content!.ReadAsStringAsync();
            return new HttpResponseMessage(HttpStatusCode.OK);
        });
        using HttpClient client = Client(server, "token-client");
        SpringClientDecisionSubmission submission = new(client);

        ClientDecisionOutcome result = await submission.SubmitAsync(
            "42",
            new BackendLabClientDecisionRequest(ClientDecisionKind.Rejection, Justification: "Motivo da recusa"),
            CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result);
        Assert.Equal("https://spring.test/api/client/quotes/42/rejection", sentPath);
        Assert.Equal("Bearer token-client", sentAuthorization);
        using JsonDocument body = JsonDocument.Parse(sentBody!);
        Assert.Equal("Motivo da recusa", body.RootElement.GetProperty("justification").GetString());
    }

    [Fact]
    public async Task EnviaRecusaSemJustificativa()
    {
        string? sentBody = null;
        using HttpClient client = Client(new ResponseHandler(async (request, _) =>
        {
            sentBody = await request.Content!.ReadAsStringAsync();
            return new HttpResponseMessage(HttpStatusCode.OK);
        }), "token-client");
        SpringClientDecisionSubmission submission = new(client);

        ClientDecisionOutcome result = await submission.SubmitAsync(
            "42", new BackendLabClientDecisionRequest(ClientDecisionKind.Rejection), CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result);
        using JsonDocument body = JsonDocument.Parse(sentBody!);
        Assert.Equal(JsonValueKind.Null, body.RootElement.GetProperty("justification").ValueKind);
    }

    [Fact]
    public async Task EnviaRecusaComJustificativaNoLimiteDeMilCaracteres()
    {
        string justification = new('a', FieldLimits.Justification);
        string? sentBody = null;
        using HttpClient client = Client(new ResponseHandler(async (request, _) =>
        {
            sentBody = await request.Content!.ReadAsStringAsync();
            return new HttpResponseMessage(HttpStatusCode.OK);
        }), "token-client");
        SpringClientDecisionSubmission submission = new(client);

        ClientDecisionOutcome result = await submission.SubmitAsync(
            "42", new BackendLabClientDecisionRequest(ClientDecisionKind.Rejection, Justification: justification),
            CancellationToken.None);

        Assert.Equal(ClientDecisionOutcome.Recorded, result);
        using JsonDocument body = JsonDocument.Parse(sentBody!);
        Assert.Equal(justification, body.RootElement.GetProperty("justification").GetString());
    }

    [Fact]
    public async Task RecusaNaoEnviaJustificativaAcimaDoLimiteDeMilCaracteres()
    {
        using HttpClient client = Client(new ResponseHandler((_, _) =>
            throw new InvalidOperationException("Não deve chamar o Spring.")), "token-client");
        SpringClientDecisionSubmission submission = new(client);

        await Assert.ThrowsAsync<ClientDecisionRequestRejectedException>(() => submission.SubmitAsync(
            "42",
            new BackendLabClientDecisionRequest(
                ClientDecisionKind.Rejection,
                Justification: new string('a', FieldLimits.Justification + 1)),
            CancellationToken.None));
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized, ClientDecisionOutcome.Recorded)]
    [InlineData(HttpStatusCode.Forbidden, ClientDecisionOutcome.Recorded)]
    [InlineData(HttpStatusCode.NotFound, ClientDecisionOutcome.QuoteNotFound)]
    [InlineData(HttpStatusCode.Conflict, ClientDecisionOutcome.NotAllowed)]
    public async Task TraduzOsResultadosDeAutorizacaoERegraDoSpring(HttpStatusCode status, ClientDecisionOutcome expected)
    {
        using HttpClient client = Client(new ResponseHandler((_, _) =>
            Task.FromResult(new HttpResponseMessage(status))), "token-client");
        SpringClientDecisionSubmission submission = new(client);

        if (status == HttpStatusCode.Unauthorized)
        {
            await Assert.ThrowsAsync<QuoteAuthenticationRequiredException>(() => submission.SubmitAsync(
                "42", AcceptanceRequest(), CancellationToken.None));
        }
        else if (status == HttpStatusCode.Forbidden)
        {
            await Assert.ThrowsAsync<QuoteAccessForbiddenException>(() => submission.SubmitAsync(
                "42", AcceptanceRequest(), CancellationToken.None));
        }
        else
        {
            Assert.Equal(expected, await submission.SubmitAsync("42", AcceptanceRequest(), CancellationToken.None));
        }
    }

    [Fact]
    public async Task RetornaAValidacaoInformadaPeloSpring()
    {
        using HttpClient client = Client(new ResponseHandler((_, _) => Task.FromResult(
            new HttpResponseMessage(HttpStatusCode.BadRequest)
            {
                Content = new StringContent("{\"message\":\"Informe os dados do termo\"}", Encoding.UTF8, "application/json")
            })), "token-client");
        SpringClientDecisionSubmission submission = new(client);

        ClientDecisionRequestRejectedException failure = await Assert.ThrowsAsync<ClientDecisionRequestRejectedException>(
            () => submission.SubmitAsync("42", AcceptanceRequest(), CancellationToken.None));

        Assert.Equal("Informe os dados do termo", failure.Message);
    }

    [Fact]
    public async Task RecusaQuandoOsEmailsDeCobrancaExcedemOContratoSpring()
    {
        AcceptanceTerms terms = Terms() with
        {
            Billing = Terms().Billing with
            {
                InvoiceEmails = [new string('a', 245) + "@x.test", new string('b', 245) + "@x.test"]
            }
        };
        using HttpClient client = Client(new ResponseHandler((_, _) =>
            throw new InvalidOperationException("Não deve chamar o Spring.")), "token-client");
        SpringClientDecisionSubmission submission = new(client);

        await Assert.ThrowsAsync<ClientDecisionRequestRejectedException>(() => submission.SubmitAsync(
            "42", new BackendLabClientDecisionRequest(ClientDecisionKind.Acceptance, terms), CancellationToken.None));
    }

    private static BackendLabClientDecisionRequest AcceptanceRequest() =>
        new(ClientDecisionKind.Acceptance, Terms());

    private static AcceptanceTerms Terms() => new(
        new BillingAddress(
            "EMPRESA TESTE", "12345678901234", "", "Rua de teste", "Curitiba", "PR", "Centro",
            "80000000", "Contato", "41999999999", ["financeiro@empresa.test", "fiscal@empresa.test"]),
        new AcceptanceApproval(
            "Solicitante", "Responsável", "Engenheira", "Laboratório", new DateOnly(2026, 11, 20),
            true, "resultados@empresa.test"));

    private static byte[] FileContent(string name) => Path.GetExtension(name).ToLowerInvariant() switch
    {
        ".pdf" => "%PDF-1.7 teste"u8.ToArray(),
        ".doc" => [0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0x01],
        ".docx" => [(byte)'P', (byte)'K', 0x03, 0x04, 0x01],
        _ => throw new ArgumentOutOfRangeException(nameof(name))
    };

    private static HttpClient Client(HttpMessageHandler server, string token)
    {
        HttpContextAccessor context = new();
        context.HttpContext = new DefaultHttpContext();
        context.HttpContext.Request.Headers.Authorization = $"Bearer {token}";
        SpringBearerForwardingHandler forwarder = new(context) { InnerHandler = server };
        return new HttpClient(forwarder) { BaseAddress = new Uri("https://spring.test/") };
    }

    private sealed class ResponseHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> respond) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken) => respond(request, cancellationToken);
    }
}
