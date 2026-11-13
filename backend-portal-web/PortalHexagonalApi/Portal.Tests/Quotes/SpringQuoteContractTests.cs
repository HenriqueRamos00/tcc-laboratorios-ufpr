using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json.Nodes;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Portal.Adapters.Quotes;
using Portal.Application.Quotes;
using Portal.Domain.Quotes;
using Portal.Tests.AutoLab;
using Xunit;

namespace Portal.Tests.Quotes;

public sealed class SpringQuoteContractTests
{
    private const string QuoteJson = """
        {"id":"52","code":"ORC-52","name":"Análise de óleo","status":"InProgress",
        "stage":"Em execução","description":"Análise físico-química","companyName":"EMPRESA DE ENERGIA",
        "protocol":"L-45490-2026-X",
        "externalContactName":"Ana","externalContactEmail":"ana@example.test",
        "totalPrice":1234.56,"createdDate":"2026-10-20T13:45:00-03:00"}
        """;

    [Fact]
    public async Task ListaVaziaEValidaEAutenticacaoOriginalChegaAoSpring()
    {
        using var origin = new QuoteOrigin((request, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token-cliente-A");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("[]", await response.Content.ReadAsStringAsync());
        var request = Assert.Single(origin.Requests);
        Assert.Equal("Bearer token-cliente-A", request.Authorization);
        Assert.Equal("/api/client/quotes", request.Path);
    }

    [Fact]
    public async Task SemBearerRetorna401SemConsultarSpring()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("Bearer", Assert.Single(response.Headers.WwwAuthenticate).Scheme);
        await AssertQuoteAuthenticationProblem(response);
        Assert.Empty(origin.Requests);
    }

    [Fact]
    public async Task BearerMalformadoRetorna401SemConsultarSpring()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.TryAddWithoutValidation("Authorization", "Basic token-invalido");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("Bearer", Assert.Single(response.Headers.WwwAuthenticate).Scheme);
        await AssertQuoteAuthenticationProblem(response);
        Assert.Empty(origin.Requests);
    }

    [Fact]
    public async Task CredencialEhValidadaAntesDoIdentificadorDaRota()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/identificador-invalido");

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("Bearer", Assert.Single(response.Headers.WwwAuthenticate).Scheme);
        await AssertQuoteAuthenticationProblem(response);
        Assert.Empty(origin.Requests);
    }

    [Fact]
    public async Task FiltroDeOrcamentosNaoExigeBearerNasRotasDeEnsaios()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();

        using HttpResponseMessage response = await client.GetAsync("/api/lab-results");

        Assert.NotEqual(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Empty(response.Headers.WwwAuthenticate);
        Assert.Empty(origin.Requests);
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized, HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden, HttpStatusCode.Forbidden)]
    public async Task PreservaAutenticacaoEAutorizacaoDaOrigem(HttpStatusCode originStatus, HttpStatusCode expected)
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(originStatus, "{}")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52");

        Assert.Equal(expected, response.StatusCode);
        if (expected == HttpStatusCode.Unauthorized)
        {
            Assert.Equal("Bearer", Assert.Single(response.Headers.WwwAuthenticate).Scheme);
            await AssertQuoteAuthenticationProblem(response);
        }
        else
        {
            JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
            Assert.Equal("quote-access-forbidden", (string?)problem["code"]);
            Assert.Equal("https://portal.lactec.org.br/problemas/quote-access-forbidden", (string?)problem["type"]);
            Assert.Equal("Acesso ao orçamento proibido.", (string?)problem["title"]);
            Assert.Equal("O usuário autenticado não tem acesso a este orçamento.", (string?)problem["detail"]);
        }
    }

    [Fact]
    public async Task DetalheMapeiaDadosDaOrigemEIdEscapado()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, QuoteJson)));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52");
        JsonObject quote = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("52", (string?)quote["id"]);
        Assert.Equal(1234.56m, (decimal?)quote["totalPrice"]);
        Assert.Equal("Em execução", (string?)quote["stage"]);
        Assert.Equal("L-45490-2026-X", (string?)quote["protocol"]);
        Assert.Equal(string.Empty, (string?)quote["opportunityId"]);
        Assert.Equal("/api/client/quotes/52", Assert.Single(origin.Requests).Path);
    }

    [Fact]
    public async Task ContatosNulosDaOrigemUsamStringsVaziasNoContratoPublico()
    {
        string quoteWithNullContacts = QuoteJson
            .Replace("\"externalContactName\":\"Ana\"", "\"externalContactName\":null", StringComparison.Ordinal)
            .Replace("\"externalContactEmail\":\"ana@example.test\"", "\"externalContactEmail\":null", StringComparison.Ordinal);
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, quoteWithNullContacts)));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52");
        JsonObject quote = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(string.Empty, (string?)quote["externalContactName"]);
        Assert.Equal(string.Empty, (string?)quote["externalContactEmail"]);
    }

    [Theory]
    [InlineData("null")]
    [InlineData("\"\"")]
    [InlineData("\"   \"")]
    public async Task EtapaAusenteOuVaziaDaOrigemRetorna502SemQualificacaoFicticia(string stage)
    {
        string body = QuoteJson.Replace("\"stage\":\"Em execução\"", "\"stage\":" + stage, StringComparison.Ordinal);
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, body)));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52");

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
    }

    [Theory]
    [InlineData("/api/quotes/52", HttpStatusCode.NotFound)]
    [InlineData("/api/quotes/52/document", HttpStatusCode.NotFound)]
    public async Task AusenciaDoRegistroOuPdfRetorna404(string path, HttpStatusCode expected)
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.NotFound, "{}")));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync(path);

        Assert.Equal(expected, response.StatusCode);
    }

    [Fact]
    public async Task ContratoSpringNaoExigeIndicadorDeDocumento()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, QuoteJson)));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
    }

    [Theory]
    [InlineData("servidor")]
    [InlineData("timeout")]
    [InlineData("json")]
    public async Task FalhaDeRedeTimeoutOuRespostaInvalidaRetorna502SemDetalhesDaOrigem(string failureType)
    {
        using var origin = new QuoteOrigin((_, _) => failureType switch
        {
            "servidor" => Task.FromResult(JsonResponse(HttpStatusCode.InternalServerError, "detalhe-interno")),
            "timeout" => Task.FromException<HttpResponseMessage>(new TaskCanceledException("token-secreto")),
            _ => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[{"))
        });
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token-secreto");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes");
        string problem = await response.Content.ReadAsStringAsync();

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.DoesNotContain("token-secreto", problem, StringComparison.Ordinal);
        Assert.DoesNotContain("detalhe-interno", problem, StringComparison.Ordinal);
    }

    [Fact]
    public async Task TimeoutDuranteLeituraDoJsonRetorna502()
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonStreamResponse(new BlockingReadStream())));
        using var portal = new SpringQuotesPortal(origin, TimeSpan.FromMilliseconds(80));
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes");

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
    }

    [Fact]
    public async Task TimeoutDuranteLeituraDoPdfRetorna502()
    {
        using var origin = new QuoteOrigin((request, _) => Task.FromResult(
            request.RequestUri!.AbsolutePath.EndsWith("/document", StringComparison.Ordinal)
                ? PdfStreamResponse(new BlockingReadStream())
                : JsonResponse(HttpStatusCode.OK, QuoteJson)));
        using var portal = new SpringQuotesPortal(origin, TimeSpan.FromMilliseconds(80));
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52/document");

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
    }

    [Fact]
    public async Task CancelamentoDoConsumidorInterrompeLeituraSemConverterEm502()
    {
        BlockingReadStream stream = new();
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonStreamResponse(stream)));
        using var portal = new SpringQuotesPortal(origin, Timeout.InfiniteTimeSpan);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");
        using CancellationTokenSource cancellation = new();
        Task<HttpResponseMessage> request = client.GetAsync("/api/quotes", cancellation.Token);

        await stream.Started.Task.WaitAsync(TimeSpan.FromSeconds(2));
        cancellation.Cancel();
        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => request);
        await stream.Canceled.Task.WaitAsync(TimeSpan.FromSeconds(2));
    }

    [Fact]
    public async Task PDFRealEEntregueSemGeracaoSubstituta()
    {
        byte[] bytes = [37, 80, 68, 70, 45, 49, 46, 55];
        using var origin = new QuoteOrigin((request, _) => Task.FromResult(
            request.RequestUri!.AbsolutePath.EndsWith("/document", StringComparison.Ordinal)
                ? new HttpResponseMessage(HttpStatusCode.OK)
                {
                    Content = new ByteArrayContent(bytes)
                    { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
                }
                : JsonResponse(HttpStatusCode.OK, QuoteJson)));
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient client = portal.CreateClient();
        client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "token");

        using HttpResponseMessage response = await client.GetAsync("/api/quotes/52/document");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("application/pdf", response.Content.Headers.ContentType!.MediaType);
        Assert.Equal(bytes, await response.Content.ReadAsByteArrayAsync());
    }

    [Fact]
    public async Task RequisicoesConcorrentesNaoCompartilhamBearer()
    {
        using var origin = new QuoteOrigin(async (request, cancellationToken) =>
        {
            await Task.Delay(30, cancellationToken);
            return JsonResponse(HttpStatusCode.OK, "[]");
        });
        using var portal = new SpringQuotesPortal(origin);
        using HttpClient clientA = portal.CreateClient();
        using HttpClient clientB = portal.CreateClient();
        clientA.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "cliente-A");
        clientB.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Bearer", "cliente-B");

        await Task.WhenAll(clientA.GetAsync("/api/quotes"), clientB.GetAsync("/api/quotes"));

        Assert.Equal(new[] { "Bearer cliente-A", "Bearer cliente-B" },
            origin.Requests.Select(request => request.Authorization).Order(StringComparer.Ordinal));
    }

    [Theory]
    [InlineData("..")]
    [InlineData("../admin")]
    [InlineData("%2e%2e")]
    [InlineData("-1")]
    [InlineData("0")]
    [InlineData("abc")]
    public async Task IdentificadorForaDoContratoSpringNaoAlteraOCaminhoNemConsultaOrigem(string id)
    {
        using var origin = new QuoteOrigin((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, QuoteJson)));
        using HttpClient httpClient = new(origin) { BaseAddress = new Uri("https://spring.test/") };
        SpringQuoteRepository repository = new(httpClient);
        SpringQuoteDocumentRepository documents = new(httpClient);

        Quote? quote = await repository.GetQuoteByIdAsync(id, CancellationToken.None);
        byte[]? document = await documents.GetDocumentAsync(id, CancellationToken.None);

        Assert.Null(quote);
        Assert.Null(document);
        Assert.Empty(origin.Requests);
    }

    private static HttpResponseMessage JsonResponse(HttpStatusCode status, string body) => new(status)
    {
        Content = new StringContent(body, Encoding.UTF8, "application/json")
    };

    private static async Task AssertQuoteAuthenticationProblem(HttpResponseMessage response)
    {
        JsonObject problem = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        Assert.Equal("quote-authentication-required", (string?)problem["code"]);
        Assert.Equal("https://portal.lactec.org.br/problemas/quote-authentication-required", (string?)problem["type"]);
        Assert.Equal("Autenticação necessária.", (string?)problem["title"]);
        Assert.Equal("Entre novamente para consultar os orçamentos.", (string?)problem["detail"]);
        Assert.Equal(401, (int?)problem["status"]);
    }

    private static HttpResponseMessage JsonStreamResponse(Stream stream) => new(HttpStatusCode.OK)
    {
        Content = new StreamContent(stream) { Headers = { ContentType = new MediaTypeHeaderValue("application/json") } }
    };

    private static HttpResponseMessage PdfStreamResponse(Stream stream) => new(HttpStatusCode.OK)
    {
        Content = new StreamContent(stream) { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
    };

    private sealed class SpringQuotesPortal(QuoteOrigin origin, TimeSpan? timeout = null) : WebApplicationFactory<Program>
    {
        protected override void ConfigureWebHost(IWebHostBuilder builder)
        {
            builder.UseAutoLabSettings();
            builder.UseSetting("Portal:DataSource", "backend-lab");
            builder.UseSetting("BackendLab:BaseUrl", "https://spring.test/");
            builder.ConfigureTestServices(services =>
            {
                services.AddHttpClient<IQuoteRepository, SpringQuoteRepository>(client =>
                    {
                        if (timeout is not null) client.Timeout = timeout.Value;
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => origin);
                services.AddHttpClient<IQuoteDocumentRepository, SpringQuoteDocumentRepository>(client =>
                    {
                        if (timeout is not null) client.Timeout = timeout.Value;
                    })
                    .ConfigurePrimaryHttpMessageHandler(() => origin);
            });
        }
    }

    private sealed class QuoteOrigin(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> respond) : HttpMessageHandler
    {
        public ConcurrentQueue<(string? Authorization, string? Path)> Requests { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
        {
            Requests.Enqueue((request.Headers.Authorization?.ToString(), request.RequestUri?.AbsolutePath));
            return respond(request, cancellationToken);
        }
    }

    private sealed class BlockingReadStream : Stream
    {
        public TaskCompletionSource Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource Canceled { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            Started.TrySetResult();
            try
            {
                await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
                return 0;
            }
            catch (OperationCanceledException)
            {
                Canceled.TrySetResult();
                throw;
            }
        }

        public override Task<int> ReadAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken) =>
            ReadAsync(buffer.AsMemory(offset, count), cancellationToken).AsTask();

        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override void Flush() => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
