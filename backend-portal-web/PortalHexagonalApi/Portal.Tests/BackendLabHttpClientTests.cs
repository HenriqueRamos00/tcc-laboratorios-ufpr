using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Collections.Concurrent;
using Microsoft.Extensions.Logging.Abstractions;
using Portal.Adapters.BackendLab;
using Portal.Application.Internal;
using Portal.Application.Internal.Auth;
using Portal.Application.Internal.Technicians;
using Xunit;

namespace Portal.Tests;

public sealed class BackendLabHttpClientTests
{
    [Fact]
    public async Task Login_enviaCredenciaisSemAnexarBearerEPreservaResposta()
    {
        var handler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, """
            {"accessToken":"token-java","tokenType":"Bearer","expiresIn":3600,"user":{"id":7,"name":"Ana","email":"ana@lab.com","role":"ADMIN"}}
            """)));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("token-entrada", "correlation-1"));

        BackendLabResult<InternalLoginResponse> result = await adapter.LoginAsync(
            new InternalLoginRequest("ana@lab.com", "senha"), CancellationToken.None);

        Assert.True(result.IsSuccess);
        Assert.Equal("token-java", result.Value?.AccessToken);
        Assert.Equal("ADMIN", result.Value?.User.Role);
        Assert.Equal("POST", handler.Method);
        Assert.Equal("/api/auth/internal/login", handler.PathAndQuery);
        Assert.Null(handler.Authorization);
        Assert.Equal("correlation-1", handler.CorrelationId);
        Assert.Contains("\"password\":\"senha\"", handler.Body);
    }

    [Fact]
    public async Task ListagemCodificaBuscaEOmiteStatusQuandoTodos()
    {
        var handler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("admin-token", "correlation-2"));

        BackendLabResult<IReadOnlyList<Technician>> result = await adapter.ListAsync(
            "Maria Silva/Unidade & 2", null, CancellationToken.None);

        Assert.True(result.IsSuccess);
        Assert.Empty(result.Value!);
        Assert.Equal("/api/technicians?search=Maria%20Silva%2FUnidade%20%26%202", handler.PathAndQuery);
        Assert.Equal("Bearer admin-token", handler.Authorization);
    }

    [Fact]
    public async Task CriacaoPreservaErroDeConflitoEEncaminhaBearerDaRequisicao()
    {
        var handler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.Conflict,
            "{\"message\":\"E-mail já cadastrado\"}")));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("admin-token", "correlation-3"));

        BackendLabResult<Technician> result = await adapter.CreateAsync(
            new CreateTechnicianRequest("Ana", "ana@lab.com", "DOLEO", "Óleos", TechnicianStatus.ACTIVE, "senha"),
            CancellationToken.None);

        Assert.False(result.IsSuccess);
        Assert.Equal(BackendLabFailureKind.Conflict, result.Failure?.Kind);
        Assert.Equal("E-mail já cadastrado", result.Failure?.Message);
        Assert.Equal("POST", handler.Method);
        Assert.Equal("Bearer admin-token", handler.Authorization);
        Assert.Equal("/api/technicians", handler.PathAndQuery);
    }

    [Fact]
    public async Task RequisicoesSimultaneasMantemBearerDeCadaUsuario()
    {
        var handler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var httpClient = CreateHttpClient(handler);
        var administradorA = CreateAdapter(httpClient, new StubRequestContext("token-a", "correlation-a"));
        var administradorB = CreateAdapter(httpClient, new StubRequestContext("token-b", "correlation-b"));

        await Task.WhenAll(
            administradorA.ListAsync(null, null, CancellationToken.None),
            administradorB.ListAsync(null, null, CancellationToken.None));

        Assert.Equal(2, handler.Authorizations.Count);
        Assert.Contains("Bearer token-a", handler.Authorizations);
        Assert.Contains("Bearer token-b", handler.Authorizations);
    }

    [Fact]
    public async Task FalhaDeConexaoRetornaServicoIndisponivel()
    {
        var handler = new CaptureHandler((_, _) => Task.FromException<HttpResponseMessage>(new HttpRequestException()));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("admin-token", "correlation-4"));

        BackendLabResult<IReadOnlyList<Technician>> result = await adapter.ListAsync(
            null, null, CancellationToken.None);

        Assert.Equal(BackendLabFailureKind.Unavailable, result.Failure?.Kind);
        Assert.Equal("O serviço interno está indisponível.", result.Failure?.Message);
    }

    [Fact]
    public async Task TimeoutRetornaGatewayTimeout()
    {
        var handler = new CaptureHandler((_, _) => Task.FromException<HttpResponseMessage>(new OperationCanceledException()));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("admin-token", "correlation-5"));

        BackendLabResult<IReadOnlyList<Technician>> result = await adapter.ListAsync(
            null, null, CancellationToken.None);

        Assert.Equal(BackendLabFailureKind.Timeout, result.Failure?.Kind);
    }

    [Fact]
    public async Task CancelamentoDoConsumidorPropagaSemSerConvertidoEmTimeout()
    {
        var handler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(HttpStatusCode.OK, "[]")));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("admin-token", "correlation-8"));
        using var cancellation = new CancellationTokenSource();
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() =>
            adapter.ListAsync(null, null, cancellation.Token));

        Assert.Equal(1, handler.RequestCount);
        Assert.True(handler.CancellationTokenWasCanceled);
    }

    [Fact]
    public async Task ErroDeServidorENjsonInvalidoNaoExpõemDetalhesDoJava()
    {
        var handler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(
            HttpStatusCode.InternalServerError, "{\"stackTrace\":\"detalhes internos\"}")));
        using var httpClient = CreateHttpClient(handler);
        var adapter = CreateAdapter(httpClient, new StubRequestContext("admin-token", "correlation-6"));

        BackendLabResult<IReadOnlyList<Technician>> result = await adapter.ListAsync(
            null, null, CancellationToken.None);

        Assert.Equal(BackendLabFailureKind.InvalidResponse, result.Failure?.Kind);
        Assert.DoesNotContain("detalhes internos", result.Failure?.Message);

        var invalidJsonHandler = new CaptureHandler((_, _) => Task.FromResult(JsonResponse(
            HttpStatusCode.OK, "não é json")));
        using var invalidJsonClient = CreateHttpClient(invalidJsonHandler);
        var invalidJsonAdapter = CreateAdapter(
            invalidJsonClient, new StubRequestContext("admin-token", "correlation-7"));

        BackendLabResult<IReadOnlyList<Technician>> invalidJsonResult = await invalidJsonAdapter.ListAsync(
            null, null, CancellationToken.None);

        Assert.Equal(BackendLabFailureKind.InvalidResponse, invalidJsonResult.Failure?.Kind);
    }

    private static BackendLabHttpClient CreateAdapter(HttpClient httpClient, IBackendLabRequestContext context) =>
        new(httpClient, context, NullLogger<BackendLabHttpClient>.Instance);

    private static HttpClient CreateHttpClient(CaptureHandler handler) => new(handler)
    {
        BaseAddress = new Uri("http://backend-lab:8080/")
    };

    private static HttpResponseMessage JsonResponse(HttpStatusCode statusCode, string content) => new(statusCode)
    {
        Content = new StringContent(content, Encoding.UTF8, "application/json")
    };

    private sealed class StubRequestContext(string? accessToken, string correlationId) : IBackendLabRequestContext
    {
        public string? AccessToken { get; } = accessToken;
        public string CorrelationId { get; } = correlationId;
    }

    private sealed class CaptureHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> responder) : HttpMessageHandler
    {
        public string? Method { get; private set; }
        public string? PathAndQuery { get; private set; }
        public string? Authorization { get; private set; }
        public string? CorrelationId { get; private set; }
        public string? Body { get; private set; }
        public int RequestCount { get; private set; }
        public bool CancellationTokenWasCanceled { get; private set; }
        public ConcurrentBag<string?> Authorizations { get; } = new();

        protected override async Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken)
        {
            RequestCount++;
            CancellationTokenWasCanceled = cancellationToken.IsCancellationRequested;
            Method = request.Method.Method;
            PathAndQuery = request.RequestUri?.PathAndQuery;
            Authorization = request.Headers.Authorization?.ToString();
            Authorizations.Add(Authorization);
            CorrelationId = request.Headers.GetValues("X-Correlation-ID").FirstOrDefault();
            Body = request.Content is null
                ? null
                : await request.Content.ReadAsStringAsync(cancellationToken);
            return await responder(request, cancellationToken);
        }
    }
}
