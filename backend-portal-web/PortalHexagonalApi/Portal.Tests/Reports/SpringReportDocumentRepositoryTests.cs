using System.Collections.Concurrent;
using System.Net;
using System.Net.Http.Headers;
using System.Text;

using Microsoft.AspNetCore.Http;

using Portal.Adapters.Reports;
using Portal.Api.Configuration;
using Portal.Application.Reports.Exceptions;

using Xunit;

namespace Portal.Tests.Reports;

public sealed class SpringReportDocumentRepositoryTests
{
    private const string PublishedReportsJson = """
        [{"id":"804","code":"804","protocol":"L-45490-2026-X","sample":"AM-2026-0142 - Óleo isolante","laboratory":"Laboratório de Ensaios","publishedOn":"2026-11-07T16:10:00Z","testType":"Análise cromatográfica"}]
        """;

    private const string QuotePublishedReportsJson = """
        {"protocol":"L-45490-2026-X","testType":"Análise cromatográfica","summary":"1 relatório publicado disponível para consulta e download.","reports":[{"id":"804","code":"804","protocol":"L-45490-2026-X","sample":"AM-2026-0142 - Óleo isolante","laboratory":"Laboratório de Ensaios","publishedOn":"2026-11-07T16:10:00Z","testType":"Análise cromatográfica"}]}
        """;

    [Fact]
    public async Task CadaConsultaEncaminhaOBearerDaRequisicaoAtualEMapeiaCamposDoRelatorio()
    {
        RecordingHandler server = new((request, _) => Task.FromResult(JsonResponse(
            request.RequestUri!.AbsolutePath.EndsWith("/reports", StringComparison.Ordinal)
                && request.RequestUri.AbsolutePath.Contains("/quotes/", StringComparison.Ordinal)
                ? QuotePublishedReportsJson
                : PublishedReportsJson)));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        SpringReportDocumentRepository repository = new(client);

        context.HttpContext!.Request.Headers.Authorization = "Bearer sessao-primeira";
        var listed = await repository.GetPublishedAsync(CancellationToken.None);

        context.HttpContext!.Request.Headers.Authorization = "Bearer sessao-segunda";
        var quote = await repository.GetPublishedOfQuoteAsync("512", CancellationToken.None);

        Assert.Equal("Bearer sessao-primeira", server.Requests.ElementAt(0).Authorization);
        Assert.Equal("/api/client/reports", server.Requests.ElementAt(0).Path);
        Assert.Equal("Bearer sessao-segunda", server.Requests.ElementAt(1).Authorization);
        Assert.Equal("/api/client/quotes/512/reports", server.Requests.ElementAt(1).Path);
        Assert.Equal("L-45490-2026-X", listed[0].Protocol);
        Assert.Equal("Laboratório de Ensaios", listed[0].Laboratory);
        Assert.Equal("804", listed[0].Code);
        Assert.Equal(listed[0].PublishedOn, listed[0].PublishedAt);
        Assert.Equal("L-45490-2026-X", quote.Protocol);
        Assert.Equal("Análise cromatográfica", quote.TestType);
        Assert.Equal("1 relatório publicado disponível para consulta e download.", quote.Summary);
        Assert.Equal(listed, quote.Reports);
    }

    [Fact]
    public async Task EnvelopeSemRelatoriosPreservaMetadadosVaziosDaFonte()
    {
        const string emptyEnvelope = """
            {"protocol":"L-45490-2026-X","testType":"","summary":"Nenhum relatório publicado disponível para consulta e download.","reports":[]}
            """;
        RecordingHandler server = new((_, _) => Task.FromResult(JsonResponse(emptyEnvelope)));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);

        var result = await repository.GetPublishedOfQuoteAsync("512", CancellationToken.None);

        Assert.Equal("L-45490-2026-X", result.Protocol);
        Assert.Equal(string.Empty, result.TestType);
        Assert.Equal("Nenhum relatório publicado disponível para consulta e download.", result.Summary);
        Assert.Empty(result.Reports);
    }

    [Theory]
    [InlineData("[]")]
    [InlineData("{\"protocol\":\"L-45490-2026-X\",\"summary\":\"1 relatório publicado disponível para consulta e download.\",\"reports\":[]}")]
    [InlineData("{\"protocol\":\"L-45490-2026-X\",\"testType\":\"Análise cromatográfica\",\"summary\":\"1 relatório publicado disponível para consulta e download.\",\"reports\":[{\"id\":\"804\",\"code\":\"804\",\"protocol\":\"L-45490-2026-X\",\"sample\":\" \",\"laboratory\":\"Laboratório de Ensaios\",\"publishedOn\":\"2026-11-07T16:10:00Z\"}]}")]
    public async Task EnvelopeOuMetadadosObrigatoriosAusentesFalhamSemCriarValores(string json)
    {
        RecordingHandler server = new((_, _) => Task.FromResult(JsonResponse(json)));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);

        await Assert.ThrowsAsync<ReportSourceUnavailableException>(
            () => repository.GetPublishedOfQuoteAsync("512", CancellationToken.None));
    }

    [Fact]
    public async Task ConsultaArquivoEncaminhaOBearerEDefineOSemTipoDoPdf()
    {
        byte[] bytes = Encoding.ASCII.GetBytes("%PDF-1.7\nconteúdo persistido");
        RecordingHandler server = new((_, _) => Task.FromResult(PdfResponse(bytes)));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        SpringReportDocumentRepository repository = new(client);
        context.HttpContext!.Request.Headers.Authorization = "Bearer sessao-atual";

        var file = await repository.GetFileAsync("804", CancellationToken.None);

        Assert.NotNull(file);
        Assert.Equal(bytes, file.Content);
        Assert.Equal("Bearer sessao-atual", Assert.Single(server.Requests).Authorization);
        Assert.Equal("/api/client/reports/804/file", Assert.Single(server.Requests).Path);
    }

    [Fact]
    public async Task TokenAusenteFalhaAntesDeConsultarAOrigem()
    {
        RecordingHandler server = new((_, _) => Task.FromResult(JsonResponse("[]")));
        using HttpClient client = CreateClient(server, out _);
        SpringReportDocumentRepository repository = new(client);

        await Assert.ThrowsAsync<ReportAuthenticationRequiredException>(
            () => repository.GetPublishedAsync(CancellationToken.None));
        Assert.Empty(server.Requests);
    }

    [Theory]
    [InlineData(HttpStatusCode.Unauthorized)]
    [InlineData(HttpStatusCode.Forbidden)]
    public async Task RespostasDeAutenticacaoEPermissaoContinuamDistintas(HttpStatusCode status)
    {
        RecordingHandler server = new((_, _) => Task.FromResult(new HttpResponseMessage(status)));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);

        if (status == HttpStatusCode.Unauthorized)
            await Assert.ThrowsAsync<ReportAuthenticationRequiredException>(
                () => repository.GetPublishedAsync(CancellationToken.None));
        else
            await Assert.ThrowsAsync<ReportAccessForbiddenException>(
                () => repository.GetPublishedAsync(CancellationToken.None));
    }

    [Fact]
    public async Task PdfAusenteNaoEhSubstituidoPorOutroArquivo()
    {
        RecordingHandler server = new((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound)));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);

        Assert.Null(await repository.GetFileAsync("804", CancellationToken.None));
    }

    [Fact]
    public async Task RespostaInvalidaOuFonteIndisponivelNaoUsaDadosDemonstrativos()
    {
        RecordingHandler invalidJsonServer = new((_, _) => Task.FromResult(JsonResponse("{}")));
        using HttpClient invalidJsonClient = CreateClient(invalidJsonServer, out HttpContextAccessor context);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository invalidJsonRepository = new(invalidJsonClient);

        await Assert.ThrowsAsync<ReportSourceUnavailableException>(
            () => invalidJsonRepository.GetPublishedAsync(CancellationToken.None));

        RecordingHandler unavailableServer = new((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)));
        using HttpClient unavailableClient = CreateClient(unavailableServer, out context);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository unavailableRepository = new(unavailableClient);

        await Assert.ThrowsAsync<ReportSourceUnavailableException>(
            () => unavailableRepository.GetPublishedAsync(CancellationToken.None));
    }

    [Fact]
    public async Task TimeoutDaFonteAbrangeODownloadDoCorpoJson()
    {
        SuspendedReadStream body = new();
        RecordingHandler server = new((_, _) => Task.FromResult(SuspendedResponse(body, "application/json")));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        client.Timeout = TimeSpan.FromSeconds(1);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);
        using CancellationTokenSource consumer = new();

        Task operation = repository.GetPublishedAsync(consumer.Token);
        await AssertSourceTimeoutAsync(operation, body.ReadStarted, consumer);
    }

    [Fact]
    public async Task TimeoutDaFonteAbrangeODownloadDoCorpoDoEnvelopePorOrcamento()
    {
        SuspendedReadStream body = new();
        RecordingHandler server = new((_, _) => Task.FromResult(SuspendedResponse(body, "application/json")));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        client.Timeout = TimeSpan.FromSeconds(1);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);
        using CancellationTokenSource consumer = new();

        Task operation = repository.GetPublishedOfQuoteAsync("512", consumer.Token);
        await AssertSourceTimeoutAsync(operation, body.ReadStarted, consumer);
    }

    [Fact]
    public async Task TimeoutDaFonteAbrangeODownloadDoCorpoPdf()
    {
        SuspendedReadStream body = new();
        RecordingHandler server = new((_, _) => Task.FromResult(SuspendedResponse(body, "application/pdf")));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        client.Timeout = TimeSpan.FromSeconds(1);
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);
        using CancellationTokenSource consumer = new();

        Task operation = repository.GetFileAsync("804", consumer.Token);
        await AssertSourceTimeoutAsync(operation, body.ReadStarted, consumer);
    }

    [Fact]
    public async Task CancelamentoDoClienteDuranteOCorpoPermaneceCancelamento()
    {
        SuspendedReadStream body = new();
        RecordingHandler server = new((_, _) => Task.FromResult(SuspendedResponse(body, "application/json")));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        client.Timeout = Timeout.InfiniteTimeSpan;
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);
        using CancellationTokenSource consumer = new();

        Task operation = repository.GetPublishedAsync(consumer.Token);
        await body.ReadStarted.WaitAsync(TimeSpan.FromSeconds(5));
        consumer.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => operation);
    }

    [Fact]
    public async Task CancelamentoDoClienteDuranteOEnvelopePermaneceCancelamento()
    {
        SuspendedReadStream body = new();
        RecordingHandler server = new((_, _) => Task.FromResult(SuspendedResponse(body, "application/json")));
        using HttpClient client = CreateClient(server, out HttpContextAccessor context);
        client.Timeout = Timeout.InfiniteTimeSpan;
        context.HttpContext!.Request.Headers.Authorization = "Bearer usuario-atual";
        SpringReportDocumentRepository repository = new(client);
        using CancellationTokenSource consumer = new();

        Task operation = repository.GetPublishedOfQuoteAsync("512", consumer.Token);
        await body.ReadStarted.WaitAsync(TimeSpan.FromSeconds(5));
        consumer.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => operation);
    }

    private static async Task AssertSourceTimeoutAsync(
        Task operation,
        Task readStarted,
        CancellationTokenSource consumer)
    {
        await readStarted.WaitAsync(TimeSpan.FromSeconds(5));
        if (await Task.WhenAny(operation, Task.Delay(TimeSpan.FromSeconds(5))) != operation)
            consumer.Cancel();

        await Assert.ThrowsAsync<ReportSourceUnavailableException>(() => operation);
    }

    private static HttpResponseMessage SuspendedResponse(SuspendedReadStream body, string mediaType) =>
        new(HttpStatusCode.OK)
        {
            Content = new StreamContent(body)
            {
                Headers = { ContentType = new MediaTypeHeaderValue(mediaType) }
            }
        };

    private static HttpClient CreateClient(RecordingHandler server, out HttpContextAccessor context)
    {
        context = new HttpContextAccessor { HttpContext = new DefaultHttpContext() };
        SpringBearerForwardingHandler forwarding = new(context) { InnerHandler = server };
        return new HttpClient(forwarding) { BaseAddress = new Uri("https://spring.test/") };
    }

    private static HttpResponseMessage JsonResponse(string json) => new(HttpStatusCode.OK)
    {
        Content = new StringContent(json, Encoding.UTF8, "application/json")
    };

    private static HttpResponseMessage PdfResponse(byte[] bytes) => new(HttpStatusCode.OK)
    {
        Content = new ByteArrayContent(bytes) { Headers = { ContentType = new MediaTypeHeaderValue("application/pdf") } }
    };

    private sealed class RecordingHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> responder) : HttpMessageHandler
    {
        public ConcurrentQueue<(string? Authorization, string? Path)> Requests { get; } = new();

        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            Requests.Enqueue((request.Headers.Authorization?.ToString(), request.RequestUri?.AbsolutePath));
            return responder(request, cancellationToken);
        }
    }

    private sealed class SuspendedReadStream : Stream
    {
        private readonly TaskCompletionSource<bool> readStarted = new(
            TaskCreationOptions.RunContinuationsAsynchronously);

        public Task ReadStarted => readStarted.Task;

        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => false;
        public override long Length => throw new NotSupportedException();
        public override long Position
        {
            get => throw new NotSupportedException();
            set => throw new NotSupportedException();
        }

        public override void Flush() => throw new NotSupportedException();
        public override int Read(byte[] buffer, int offset, int count) => throw new NotSupportedException();
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
        public override void Write(byte[] buffer, int offset, int count) => throw new NotSupportedException();

        public override ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken cancellationToken = default)
        {
            readStarted.TrySetResult(true);
            return WaitForCancellationAsync(cancellationToken);
        }

        public override Task<int> ReadAsync(
            byte[] buffer,
            int offset,
            int count,
            CancellationToken cancellationToken) =>
            ReadAsync(buffer.AsMemory(offset, count), cancellationToken).AsTask();

        private static async ValueTask<int> WaitForCancellationAsync(CancellationToken cancellationToken)
        {
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return 0;
        }
    }
}
