using System.Net;
using System.Net.Http.Headers;
using System.Text;

using Portal.Adapters.Quotes;
using Portal.Application.Quotes.Exceptions;
using Xunit;

namespace Portal.Tests.Quotes;

public sealed class SpringQuoteAdapterTests
{
    private const string QuoteJson = """
        {"id":"52","code":"ORC-52","name":"Análise de óleo","status":"InProgress",
        "stage":"Em execução","description":"Análise físico-química","companyName":"EMPRESA DE ENERGIA",
        "externalContactName":"Ana","externalContactEmail":"ana@example.test",
        "totalPrice":1234.56,"createdDate":"2026-10-20T13:45:00-03:00"}
        """;

    [Fact]
    public async Task ListaEMapeiaContratoTipadoEContatosNulos()
    {
        string json = QuoteJson
            .Replace("\"externalContactName\":\"Ana\"", "\"externalContactName\":null", StringComparison.Ordinal)
            .Replace("\"externalContactEmail\":\"ana@example.test\"", "\"externalContactEmail\":null", StringComparison.Ordinal)
            .Replace("\"createdDate\":\"2026-10-20T13:45:00-03:00\"", "\"createdDate\":null", StringComparison.Ordinal);
        using var handler = new ResponseHandler(_ => JsonResponse(HttpStatusCode.OK, $"[{json}]"));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        var quote = Assert.Single(await repository.GetQuotesAsync(CancellationToken.None));

        Assert.Equal("52", quote.Id);
        Assert.Equal(string.Empty, quote.OpportunityId);
        Assert.Equal("Em execução", quote.Stage);
        Assert.Equal(1234.56m, quote.TotalPrice);
        Assert.Equal(string.Empty, quote.ExternalContactName);
        Assert.Equal(string.Empty, quote.ExternalContactEmail);
        Assert.Null(quote.CreatedDate);
    }

    [Fact]
    public async Task ListaVaziaPermaneceValida()
    {
        using var handler = new ResponseHandler(_ => JsonResponse(HttpStatusCode.OK, "[]"));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        Assert.Empty(await repository.GetQuotesAsync(CancellationToken.None));
    }

    [Theory]
    [InlineData("{}")]
    [InlineData("null")]
    [InlineData("[null]")]
    [InlineData("[{\"id\":52}]")]
    [InlineData("[{\"id\":\"52\"}]")]
    public async Task ContratoIncompletoOuComTiposIncorretosFalhaComoOrigemIndisponivel(string body)
    {
        using var handler = new ResponseHandler(_ => JsonResponse(HttpStatusCode.OK, body));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetQuotesAsync(CancellationToken.None));
    }

    [Theory]
    [InlineData("stage", "null")]
    [InlineData("stage", "\" \"")]
    [InlineData("id", "\" \"")]
    [InlineData("totalPrice", "\"1234.56\"")]
    [InlineData("totalPrice", "null")]
    [InlineData("createdDate", "\"ontem\"")]
    [InlineData("name", "null")]
    public async Task CampoPresenteMasInvalidoFalhaComoOrigemIndisponivel(string field, string value)
    {
        string body = QuoteJson.Replace($"\"{field}\":{OriginalValue(field)}", $"\"{field}\":{value}", StringComparison.Ordinal);
        using var handler = new ResponseHandler(_ => JsonResponse(HttpStatusCode.OK, body));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetQuoteByIdAsync("52", CancellationToken.None));
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden)]
    public async Task PreservaAutenticacaoEAutorizacao(HttpStatusCode status)
    {
        using var handler = new ResponseHandler(_ => JsonResponse(status, "{}"));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        if (status == HttpStatusCode.Unauthorized)
            await Assert.ThrowsAsync<QuoteAuthenticationRequiredException>(
                () => repository.GetQuoteByIdAsync("52", CancellationToken.None));
        else
            await Assert.ThrowsAsync<QuoteAccessForbiddenException>(
                () => repository.GetQuoteByIdAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task IdInexistenteRetornaNulo()
    {
        using var handler = new ResponseHandler(_ => JsonResponse(HttpStatusCode.NotFound, "{}"));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        Assert.Null(await repository.GetQuoteByIdAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task PdfPreservaOsBytesRecebidos()
    {
        byte[] pdf = [37, 80, 68, 70, 45, 49, 46, 55, 0, 255];
        using var handler = new ResponseHandler(_ => PdfResponse(HttpStatusCode.OK, pdf));
        using var client = CreateClient(handler);
        SpringQuoteDocumentRepository repository = new(client);

        Assert.Equal(pdf, await repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task TipoDeMidiaPdfNaoDiferenciaMaiusculas()
    {
        byte[] pdf = [37, 80, 68, 70];
        using var handler = new ResponseHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(pdf)
            { Headers = { ContentType = new MediaTypeHeaderValue("Application/PDF") } }
        });
        using var client = CreateClient(handler);
        SpringQuoteDocumentRepository repository = new(client);

        Assert.Equal(pdf, await repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden)]
    public async Task DocumentoPreservaAutenticacaoEAutorizacao(HttpStatusCode status)
    {
        using var handler = new ResponseHandler(_ => PdfResponse(status, [1]));
        using var client = CreateClient(handler);
        SpringQuoteDocumentRepository repository = new(client);

        if (status == HttpStatusCode.Unauthorized)
            await Assert.ThrowsAsync<QuoteAuthenticationRequiredException>(
                () => repository.GetDocumentAsync("52", CancellationToken.None));
        else
            await Assert.ThrowsAsync<QuoteAccessForbiddenException>(
                () => repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task DocumentoInexistenteRetornaNulo()
    {
        using var handler = new ResponseHandler(_ => PdfResponse(HttpStatusCode.NotFound, []));
        using var client = CreateClient(handler);
        SpringQuoteDocumentRepository repository = new(client);

        Assert.Null(await repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task FalhaAoLerPdfEhFalhaDeDocumento()
    {
        using var handler = new ResponseHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StreamContent(new FailingReadStream())
            { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
        });
        using var client = CreateClient(handler);
        SpringQuoteDocumentRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteDocumentUnavailableException>(
            () => repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Theory]
    [InlineData(HttpStatusCode.InternalServerError, "application/pdf", new byte[] { 1 })]
    [InlineData(HttpStatusCode.OK, "application/json", new byte[] { 1 })]
    [InlineData(HttpStatusCode.OK, "application/pdf", new byte[] { })]
    public async Task DocumentoComStatusTipoOuConteudoInvalidoFalhaComoDocumento(
        HttpStatusCode status, string mediaType, byte[] content)
    {
        using var handler = new ResponseHandler(_ => new HttpResponseMessage(status)
        {
            Content = new ByteArrayContent(content) { Headers = { ContentType = new MediaTypeHeaderValue(mediaType) } }
        });
        using var client = CreateClient(handler);
        SpringQuoteDocumentRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteDocumentUnavailableException>(
            () => repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task FalhaDeRedeEhOrigemIndisponivel()
    {
        using var handler = new ResponseHandler(_ =>
            Task.FromException<HttpResponseMessage>(new HttpRequestException("falha interna")));
        using var client = CreateClient(handler);
        SpringQuoteRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetQuotesAsync(CancellationToken.None));
    }

    [Fact]
    public async Task TimeoutDuranteEnvioDoPdfEhOrigemIndisponivel()
    {
        using var handler = new ResponseHandler(async cancellationToken =>
        {
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return PdfResponse(HttpStatusCode.OK, [1]);
        });
        using var client = CreateClient(handler, TimeSpan.FromMilliseconds(40));
        SpringQuoteDocumentRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task TimeoutDuranteLeituraDoPdfEhOrigemIndisponivel()
    {
        using var handler = new ResponseHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StreamContent(new BlockingReadStream())
            { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
        });
        using var client = CreateClient(handler, TimeSpan.FromMilliseconds(40));
        SpringQuoteDocumentRepository repository = new(client);

        await Assert.ThrowsAsync<QuoteSourceUnavailableException>(
            () => repository.GetDocumentAsync("52", CancellationToken.None));
    }

    [Fact]
    public async Task CancelamentoDoConsumidorPropagaOperacaoCancelada()
    {
        BlockingReadStream stream = new();
        using var handler = new ResponseHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new StreamContent(stream) { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
        });
        using var client = CreateClient(handler, Timeout.InfiniteTimeSpan);
        SpringQuoteDocumentRepository repository = new(client);
        using CancellationTokenSource cancellation = new();
        Task<byte[]?> operation = repository.GetDocumentAsync("52", cancellation.Token);

        await stream.Started.Task.WaitAsync(TimeSpan.FromSeconds(2));
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => operation);
    }

    private static string OriginalValue(string field) => field switch
    {
        "stage" => "\"Em execução\"",
        "id" => "\"52\"",
        "totalPrice" => "1234.56",
        "createdDate" => "\"2026-10-20T13:45:00-03:00\"",
        "name" => "\"Análise de óleo\"",
        _ => throw new ArgumentOutOfRangeException(nameof(field))
    };

    private static HttpClient CreateClient(HttpMessageHandler handler, TimeSpan? timeout = null) => new(handler)
    {
        BaseAddress = new Uri("https://spring.test/"),
        Timeout = timeout ?? TimeSpan.FromSeconds(5)
    };

    private static HttpResponseMessage JsonResponse(HttpStatusCode status, string body) => new(status)
    {
        Content = new StringContent(body, Encoding.UTF8, "application/json")
    };

    private static HttpResponseMessage PdfResponse(HttpStatusCode status, byte[] bytes) => new(status)
    {
        Content = new ByteArrayContent(bytes) { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
    };

    private sealed class ResponseHandler : HttpMessageHandler
    {
        private readonly Func<CancellationToken, Task<HttpResponseMessage>> respond;

        public ResponseHandler(Func<CancellationToken, HttpResponseMessage> respond)
            : this(token => Task.FromResult(respond(token)))
        {
        }

        public ResponseHandler(Func<CancellationToken, Task<HttpResponseMessage>> respond)
        {
            this.respond = respond;
        }

        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) =>
            respond(cancellationToken);
    }

    private sealed class BlockingReadStream : Stream
    {
        public TaskCompletionSource Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override void Flush() => throw new NotSupportedException();
        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            Started.TrySetResult();
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return 0;
        }
    }

    private sealed class FailingReadStream : Stream
    {
        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override void Flush() => throw new NotSupportedException();
        public override int Read(byte[] buffer, int offset, int count) => throw new IOException("falha de leitura");
        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default) =>
            ValueTask.FromException<int>(new IOException("falha de leitura"));
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();
    }
}
