using Portal.Application.ReportProjection;

namespace Portal.Api.ReportProjection;

public sealed class ReportRefreshBackgroundService : BackgroundService
{
    private readonly IServiceScopeFactory _scopes;
    private readonly ReportLoadInProgress _inProgress;
    private readonly ILogger<ReportRefreshBackgroundService> _logger;

    public ReportRefreshBackgroundService(
        IServiceScopeFactory scopes,
        ReportLoadInProgress inProgress,
        ILogger<ReportRefreshBackgroundService> logger)
    {
        _scopes = scopes;
        _inProgress = inProgress;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await _inProgress.WaitForRequestsAsync(stoppingToken);
                await ServeAsync(stoppingToken);
            }
            catch (OperationCanceledException)
            {
                return;
            }
            catch (Exception failure)
            {
                _logger.LogError(failure, "Atualizacao da situacao: erro inesperado ao atender os pedidos.");
            }
        }
    }

    private async Task ServeAsync(CancellationToken cancellationToken)
    {
        await using AsyncServiceScope scope = _scopes.CreateAsyncScope();

        ReportLoadResult result = await scope.ServiceProvider
            .GetRequiredService<IReportLoad>()
            .ServeRequestsAsync(cancellationToken);

        if (result.AlreadyRunning)
        {
            _logger.LogInformation("Atualizacao da situacao: os pedidos ficam com a rodada em andamento.");
            return;
        }

        if (result.Success)
        {
            _logger.LogInformation(
                "Atualizacao da situacao concluida: {Read} PDF(s) lido(s), {Equipment} equipamento(s) atualizado(s).",
                result.ReportsRead, result.EquipmentUpdated);
            return;
        }

        _logger.LogWarning(
            "Atualizacao da situacao concluida com {Count} problema(s): {Problems}.",
            result.Problems.Count, string.Join(" | ", result.Problems));
    }
}
