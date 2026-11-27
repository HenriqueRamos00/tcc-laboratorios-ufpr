using Portal.Application.ReportProjection;

namespace Portal.Api.ReportProjection;

public sealed class ReportLoadBackgroundService : BackgroundService
{
    private readonly IServiceScopeFactory _scopes;
    private readonly ReportLoadSchedule _schedule;
    private readonly TimeProvider _time;
    private readonly ILogger<ReportLoadBackgroundService> _logger;

    public ReportLoadBackgroundService(
        IServiceScopeFactory scopes,
        ReportLoadSchedule schedule,
        TimeProvider time,
        ILogger<ReportLoadBackgroundService> logger)
    {
        _scopes = scopes;
        _schedule = schedule;
        _time = time;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (await IsDueAtStartupAsync(stoppingToken))
        {
            _logger.LogInformation("Carga dos laudos: rodando na subida.");
            await RunWithRetryAsync(stoppingToken);
        }

        while (!stoppingToken.IsCancellationRequested)
        {
            if (!await WaitUntilAsync(_schedule.NextRun(_time.GetUtcNow()), stoppingToken)) break;

            await RunWithRetryAsync(stoppingToken);
        }
    }

    private async Task RunWithRetryAsync(CancellationToken stoppingToken)
    {
        if (await RunAsync(stoppingToken)) return;

        DateTimeOffset? retry = _schedule.RetryOfToday(_time.GetUtcNow());

        if (retry is null) return;

        _logger.LogInformation("Carga dos laudos: nova tentativa marcada para {Retry}.", retry);

        if (await WaitUntilAsync(retry.Value, stoppingToken)) await RunAsync(stoppingToken);
    }

    private async Task<bool> IsDueAtStartupAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using AsyncServiceScope scope = _scopes.CreateAsyncScope();
            DateTimeOffset now = _time.GetUtcNow();

            return await scope.ServiceProvider.GetRequiredService<IReportLoad>().IsDueAtStartupAsync(
                _schedule.StartOfTodayUtc(now),
                _schedule.IsPastTheDailyTime(now),
                cancellationToken);
        }
        catch (Exception failure) when (failure is not OperationCanceledException)
        {
            _logger.LogWarning(failure, "Carga dos laudos: nao foi possivel conferir se a rodada da subida e necessaria.");
            return false;
        }
    }

    private async Task<bool> RunAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using AsyncServiceScope scope = _scopes.CreateAsyncScope();

            ReportLoadResult result = await scope.ServiceProvider
                .GetRequiredService<IReportLoad>()
                .RunDailyAsync(cancellationToken);

            if (result.AlreadyRunning)
            {
                _logger.LogInformation("Carga dos laudos: ja havia uma rodada em andamento; esta nao foi aberta.");
                return true;
            }

            if (result.Success)
            {
                _logger.LogInformation(
                    "Carga dos laudos concluida: {Read} PDF(s) lido(s), {Unreadable} ilegivel(is), {Equipment} equipamento(s) atualizado(s).",
                    result.ReportsRead, result.ReportsUnreadable, result.EquipmentUpdated);
                return true;
            }

            _logger.LogWarning(
                "Carga dos laudos concluida com {Count} problema(s): {Problems}. {Read} PDF(s) lido(s).",
                result.Problems.Count, string.Join(" | ", result.Problems), result.ReportsRead);
            return false;
        }
        catch (OperationCanceledException)
        {
            _logger.LogInformation("Carga dos laudos: rodada interrompida pela parada da API.");
            return true;
        }
        catch (Exception failure)
        {
            _logger.LogError(failure, "Carga dos laudos: erro inesperado durante a rodada.");
            return false;
        }
    }

    private async Task<bool> WaitUntilAsync(DateTimeOffset moment, CancellationToken stoppingToken)
    {
        TimeSpan wait = moment - _time.GetUtcNow();

        if (wait <= TimeSpan.Zero) return true;

        try
        {
            await Task.Delay(wait, _time, stoppingToken);
            return true;
        }
        catch (OperationCanceledException)
        {
            return false;
        }
    }
}
