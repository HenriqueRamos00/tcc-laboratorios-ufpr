using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Options;

using Portal.Adapters.PortalStore;

namespace Portal.Api.PortalStore;

public sealed class PortalStoreMigration(
    IServiceScopeFactory scopes,
    IOptions<PortalStoreOptions> options,
    ILogger<PortalStoreMigration> logger) : IHostedService
{
    public async Task StartAsync(CancellationToken cancellationToken)
    {
        int attempts = options.Value.MigrationAttempts;

        for (int attempt = 1; ; attempt++)
        {
            try
            {
                await using AsyncServiceScope scope = scopes.CreateAsyncScope();
                PortalStoreDbContext database = scope.ServiceProvider.GetRequiredService<PortalStoreDbContext>();
                await database.Database.MigrateAsync(cancellationToken);
                logger.LogInformation("Banco do portal atualizado.");
                return;
            }
            catch (Exception failure) when (attempt < attempts && failure is not OperationCanceledException)
            {
                logger.LogWarning(failure, "Banco do portal indisponivel, nova tentativa {Attempt} de {Attempts}.", attempt + 1, attempts);
                await Task.Delay(TimeSpan.FromSeconds(3), cancellationToken);
            }
        }
    }

    public Task StopAsync(CancellationToken cancellationToken) => Task.CompletedTask;
}
