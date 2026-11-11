using Microsoft.EntityFrameworkCore;

namespace Portal.Adapters.AutoLab;

internal static class AutoLabFetch
{
    public const string QueryFailed = "A consulta ao banco do AutoLAB falhou.";

    public static async Task<T> GuardAsync<T>(
        Func<Task<T>> read,
        Func<string, Exception, Exception> failure)
    {
        try
        {
            return await read();
        }
        catch (Exception exception) when (exception is not OperationCanceledException)
        {
            throw failure(QueryFailed, exception);
        }
    }

    public static async Task<List<T>> ListAsync<T>(
        IQueryable<T> query,
        Func<string, Exception, Exception> failure,
        CancellationToken cancellationToken)
    {
        try
        {
            return await query.ToListAsync(cancellationToken);
        }
        catch (Exception exception) when (exception is not OperationCanceledException)
        {
            throw failure(QueryFailed, exception);
        }
    }

    public static async Task<List<T>> BoundedListAsync<T>(
        IQueryable<T> queryLimitedToMaxPlusOne,
        int maxRows,
        Func<string, Exception, Exception> failure,
        CancellationToken cancellationToken)
    {
        List<T> rows = await ListAsync(queryLimitedToMaxPlusOne, failure, cancellationToken);

        if (rows.Count > maxRows)
            throw failure(
                $"A consulta ao banco do AutoLAB passou do limite de {maxRows} linhas.",
                new InvalidOperationException("Limite de linhas por consulta excedido."));

        return rows;
    }

    public static async Task<List<T>> BoundedListInChunksAsync<T>(
        IReadOnlyCollection<string> values,
        AutoLabQueryLimits limits,
        Func<List<string>, int, IQueryable<T>> queryOfChunk,
        Func<string, Exception, Exception> failure,
        CancellationToken cancellationToken)
    {
        List<T> rows = [];

        foreach (string[] chunk in values.Chunk(limits.MaxValuesPerInClause))
        {
            int remaining = limits.MaxRowsPerQuery - rows.Count;

            rows.AddRange(await BoundedListAsync(
                queryOfChunk([.. chunk], remaining + 1),
                remaining,
                failure,
                cancellationToken));
        }

        return rows;
    }
}
