using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

using Portal.Adapters.PortalStore;
using Portal.Application.RealTime;
using Portal.Application.ReportDocuments;
using Portal.Application.ReportProjection;

namespace Portal.Tests.ReportProjection;

internal static class TestProjection
{
    public static EquipmentProjection Of(IReportIssueRepository reports)
    {
        return new EquipmentProjection(new NotConnectedReportProjectionStore(), reports, TimeProvider.System);
    }
}

internal sealed class ProjectionTestStore : IDisposable
{
    private readonly SqliteConnection _connection = new("Data Source=:memory:");

    public ProjectionTestStore()
    {
        _connection.Open();
        using PortalStoreDbContext database = Context();
        database.Database.EnsureCreated();
    }

    public SqliteConnection Connection => _connection;

    public PortalStoreDbContext Context() =>
        new(new DbContextOptionsBuilder<PortalStoreDbContext>().UseSqlite(_connection).Options);

    public SqlServerReportProjectionStore Store() => new(Context());

    public void Activate(string cnpj, bool active = true)
    {
        using PortalStoreDbContext database = Context();
        database.LoadScopes.Add(new LoadScopeRow { Cnpj = cnpj, Active = active });
        database.SaveChanges();
    }

    public LoadScopeRow ScopeOf(string cnpj)
    {
        using PortalStoreDbContext database = Context();

        return database.LoadScopes.AsNoTracking().Single(row => row.Cnpj == cnpj);
    }

    public List<ReportReadingRow> Readings()
    {
        using PortalStoreDbContext database = Context();

        return [.. database.ReportReadings.AsNoTracking().Include(row => row.Sections).OrderBy(row => row.Identification)];
    }

    public List<EquipmentStatusRow> Statuses()
    {
        using PortalStoreDbContext database = Context();

        return [.. database.EquipmentStatuses.AsNoTracking().OrderBy(row => row.EquipmentKey)];
    }

    public void Dispose() => _connection.Dispose();
}

internal sealed class MovableTime(DateTimeOffset start) : TimeProvider
{
    private DateTimeOffset _now = start;

    public override DateTimeOffset GetUtcNow() => _now;

    public void Advance(TimeSpan span) => _now += span;
}

internal sealed class RecordedChannel : IRealTimeChannel
{
    public List<(string Name, string DataJson, string[] UserKeys)> Published { get; } = [];

    public void Publish(string name, string dataJson, IReadOnlySet<string> userKeys)
    {
        Published.Add((name, dataJson, [.. userKeys.Order(StringComparer.Ordinal)]));
    }
}
