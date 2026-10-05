using System.Net;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.Logging;
using Portal.Application.Internal;
using Portal.Application.Internal.Auth;
using Portal.Application.Internal.Technicians;

namespace Portal.Adapters.BackendLab;

public sealed class BackendLabHttpClient : IInternalAuthPort, ITechniciansPort
{
    private const string ApiPrefix = "api/";
    private const string IntegrationErrorMessage = "Não foi possível interpretar a resposta do Backend Lab.";
    private const string UnavailableMessage = "O serviço interno está indisponível.";
    private static readonly JsonSerializerOptions JsonOptions = CreateJsonOptions();

    private readonly HttpClient _httpClient;
    private readonly IBackendLabRequestContext _requestContext;
    private readonly ILogger<BackendLabHttpClient> _logger;

    public BackendLabHttpClient(
        HttpClient httpClient,
        IBackendLabRequestContext requestContext,
        ILogger<BackendLabHttpClient> logger)
    {
        _httpClient = httpClient;
        _requestContext = requestContext;
        _logger = logger;
    }

    public Task<BackendLabResult<InternalLoginResponse>> LoginAsync(
        InternalLoginRequest request,
        CancellationToken cancellationToken) =>
        SendAsync<InternalLoginResponse>(
            HttpMethod.Post,
            $"{ApiPrefix}auth/internal/login",
            request,
            forwardAccessToken: false,
            cancellationToken);

    public Task<BackendLabResult<IReadOnlyList<Technician>>> ListAsync(
        string? search,
        TechnicianStatus? status,
        CancellationToken cancellationToken)
    {
        var query = new List<string>();
        if (!string.IsNullOrWhiteSpace(search))
            query.Add($"search={Uri.EscapeDataString(search)}");
        if (status is not null)
            query.Add($"status={Uri.EscapeDataString(status.Value.ToString())}");

        string path = $"{ApiPrefix}technicians";
        if (query.Count > 0) path += "?" + string.Join("&", query);

        return SendAsync<IReadOnlyList<Technician>>(
            HttpMethod.Get,
            path,
            body: null,
            forwardAccessToken: true,
            cancellationToken);
    }

    public Task<BackendLabResult<Technician>> CreateAsync(
        CreateTechnicianRequest request,
        CancellationToken cancellationToken) =>
        SendAsync<Technician>(
            HttpMethod.Post,
            $"{ApiPrefix}technicians",
            request,
            forwardAccessToken: true,
            cancellationToken);

    public Task<BackendLabResult<Technician>> UpdateAsync(
        long id,
        UpdateTechnicianRequest request,
        CancellationToken cancellationToken) =>
        SendAsync<Technician>(
            HttpMethod.Put,
            $"{ApiPrefix}technicians/{id}",
            request,
            forwardAccessToken: true,
            cancellationToken);

    public Task<BackendLabResult<Technician>> ChangeStatusAsync(
        long id,
        TechnicianStatus status,
        CancellationToken cancellationToken) =>
        SendAsync<Technician>(
            HttpMethod.Patch,
            $"{ApiPrefix}technicians/{id}/status",
            new ChangeTechnicianStatusRequest(status),
            forwardAccessToken: true,
            cancellationToken);

    private async Task<BackendLabResult<T>> SendAsync<T>(
        HttpMethod method,
        string path,
        object? body,
        bool forwardAccessToken,
        CancellationToken cancellationToken)
    {
        string correlationId = _requestContext.CorrelationId;
        using var request = new HttpRequestMessage(method, path);
        if (body is not null)
            request.Content = JsonContent.Create(body, body.GetType(), options: JsonOptions);

        request.Headers.TryAddWithoutValidation("X-Correlation-ID", correlationId);
        if (forwardAccessToken && !string.IsNullOrWhiteSpace(_requestContext.AccessToken))
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _requestContext.AccessToken);

        try
        {
            using HttpResponseMessage response = await _httpClient.SendAsync(
                request,
                HttpCompletionOption.ResponseContentRead,
                cancellationToken);

            _logger.LogInformation(
                "Requisição ao Backend Lab concluída. CorrelationId: {CorrelationId}; Método: {Method}; Caminho: {Path}; Status: {Status}",
                correlationId, method.Method, LogPath(path), (int)response.StatusCode);

            if (!response.IsSuccessStatusCode)
                return await ReadFailureAsync<T>(response, path, correlationId, cancellationToken);

            try
            {
                T? value = await response.Content.ReadFromJsonAsync<T>(JsonOptions, cancellationToken);
                if (value is null)
                {
                    _logger.LogWarning(
                        "Backend Lab retornou corpo vazio. CorrelationId: {CorrelationId}; Path: {Path}",
                        correlationId, LogPath(path));
                    return Failed<T>(BackendLabFailureKind.InvalidResponse, IntegrationErrorMessage);
                }

                return BackendLabResult<T>.Success(value);
            }
            catch (JsonException exception)
            {
                _logger.LogWarning(
                    "Backend Lab retornou JSON inválido. CorrelationId: {CorrelationId}; Path: {Path}; ErrorType: {ErrorType}",
                    correlationId, LogPath(path), exception.GetType().Name);
                return Failed<T>(BackendLabFailureKind.InvalidResponse, IntegrationErrorMessage);
            }
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            _logger.LogWarning(
                "Timeout na integração com Backend Lab. CorrelationId: {CorrelationId}; Path: {Path}",
                correlationId, LogPath(path));
            return Failed<T>(BackendLabFailureKind.Timeout, "O Backend Lab excedeu o tempo de resposta.");
        }
        catch (HttpRequestException exception)
        {
            _logger.LogWarning(
                "Falha de conexão com Backend Lab. CorrelationId: {CorrelationId}; Path: {Path}; ErrorType: {ErrorType}",
                correlationId, LogPath(path), exception.GetType().Name);
            return Failed<T>(BackendLabFailureKind.Unavailable, UnavailableMessage);
        }
    }

    private async Task<BackendLabResult<T>> ReadFailureAsync<T>(
        HttpResponseMessage response,
        string path,
        string correlationId,
        CancellationToken cancellationToken)
    {
        if ((int)response.StatusCode >= 500)
        {
            _logger.LogWarning(
                "Backend Lab retornou erro de servidor. CorrelationId: {CorrelationId}; Path: {Path}; Status: {Status}",
                correlationId, LogPath(path), (int)response.StatusCode);
            return Failed<T>(BackendLabFailureKind.InvalidResponse, "Ocorreu uma falha no serviço interno.");
        }

        string message = await ReadPublicMessageAsync(response, cancellationToken);
        string? wwwAuthenticate = response.Headers.WwwAuthenticate.FirstOrDefault()?.ToString();

        return response.StatusCode switch
        {
            HttpStatusCode.BadRequest => Failed<T>(BackendLabFailureKind.InvalidInput, message),
            HttpStatusCode.Unauthorized => Failed<T>(BackendLabFailureKind.Unauthorized, message, wwwAuthenticate),
            HttpStatusCode.Forbidden => Failed<T>(BackendLabFailureKind.Forbidden, message),
            HttpStatusCode.NotFound => Failed<T>(BackendLabFailureKind.NotFound, message),
            HttpStatusCode.Conflict => Failed<T>(BackendLabFailureKind.Conflict, message),
            _ => Failed<T>(BackendLabFailureKind.InvalidResponse, "Ocorreu uma falha no serviço interno.")
        };
    }

    private static async Task<string> ReadPublicMessageAsync(
        HttpResponseMessage response,
        CancellationToken cancellationToken)
    {
        try
        {
            BackendLabErrorResponse? error = await response.Content.ReadFromJsonAsync<BackendLabErrorResponse>(
                JsonOptions,
                cancellationToken);
            if (!string.IsNullOrWhiteSpace(error?.Message))
            {
                return error.Message;
            }
        }
        catch (JsonException)
        {
            // Uma mensagem fora do contrato não deve ser exposta ao consumidor.
        }

        return response.StatusCode switch
        {
            HttpStatusCode.BadRequest => "Dados inválidos.",
            HttpStatusCode.Unauthorized => "Não autenticado.",
            HttpStatusCode.Forbidden => "Sem permissão.",
            HttpStatusCode.NotFound => "Registro não encontrado.",
            HttpStatusCode.Conflict => "O registro informado já existe.",
            _ => "Ocorreu uma falha no serviço interno."
        };
    }

    private static string LogPath(string path) => path.Split('?', 2)[0];

    private static BackendLabResult<T> Failed<T>(
        BackendLabFailureKind kind,
        string message,
        string? wwwAuthenticate = null) =>
        BackendLabResult<T>.Failed(new BackendLabFailure(kind, message, wwwAuthenticate));

    private static JsonSerializerOptions CreateJsonOptions()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web);
        options.Converters.Add(new JsonStringEnumConverter(allowIntegerValues: false));
        return options;
    }

    private sealed record BackendLabErrorResponse(string? Message);
}
