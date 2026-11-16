using Microsoft.Data.SqlClient;
using Microsoft.EntityFrameworkCore;

using Portal.Application.Quotes;
using Portal.Application.Quotes.Exceptions;
using Portal.Domain.Quotes;

namespace Portal.Adapters.PortalStore;

public sealed class SqlServerClientDecisionRepository : IClientDecisionRepository
{
    private const int UniqueIndexViolation = 2601;
    private const int UniqueConstraintViolation = 2627;

    private readonly PortalStoreDbContext _database;

    public SqlServerClientDecisionRepository(PortalStoreDbContext database)
    {
        _database = database;
    }

    public async Task AddAsync(ClientDecision decision, CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(decision);

        ClientDecisionRow row = ClientDecisionMapping.ToRow(decision);
        _database.ClientDecisions.Add(row);

        try
        {
            await _database.SaveChangesAsync(cancellationToken);
        }
        catch (DbUpdateException failure)
        {
            _database.ChangeTracker.Clear();

            if (failure.InnerException is SqlException { Number: UniqueIndexViolation or UniqueConstraintViolation }
                || await SlotIsTakenAsync(row, cancellationToken))
                throw new ClientDecisionConflictException("O orcamento ja tem essa decisao registrada.", failure);

            throw new ClientDecisionStoreUnavailableException("O banco do portal recusou a gravacao.", failure);
        }
        catch (Exception failure) when (failure is SqlException or InvalidOperationException or TimeoutException)
        {
            throw new ClientDecisionStoreUnavailableException("O banco do portal nao respondeu.", failure);
        }
    }

    public async Task<IReadOnlyList<ClientDecision>> GetSummariesOfQuotesAsync(
        IReadOnlyCollection<string> quoteIds,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(quoteIds);

        if (quoteIds.Count == 0) return [];

        List<string> wanted = [.. quoteIds];

        try
        {
            List<ClientDecisionRow> rows = await _database.ClientDecisions
                .AsNoTracking()
                .Where(row => wanted.Contains(row.QuoteId))
                .ToListAsync(cancellationToken);

            return [.. rows.Select(ClientDecisionMapping.ToSummary)];
        }
        catch (Exception failure) when (failure is SqlException or InvalidOperationException or TimeoutException)
        {
            throw new ClientDecisionStoreUnavailableException("O banco do portal nao respondeu.", failure);
        }
    }

    private Task<bool> SlotIsTakenAsync(ClientDecisionRow row, CancellationToken cancellationToken)
    {
        return _database.ClientDecisions
            .AsNoTracking()
            .AnyAsync(existing => existing.QuoteId == row.QuoteId && existing.Slot == row.Slot, cancellationToken);
    }
}
