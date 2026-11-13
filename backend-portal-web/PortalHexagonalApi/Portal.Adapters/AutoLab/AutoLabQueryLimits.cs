namespace Portal.Adapters.AutoLab;

public sealed class AutoLabQueryLimits
{
    public const int DefaultMaxRowsPerQuery = 5000;
    public const int DefaultMaxValuesPerInClause = 500;

    public AutoLabQueryLimits(
        int maxRowsPerQuery = DefaultMaxRowsPerQuery,
        int maxValuesPerInClause = DefaultMaxValuesPerInClause)
    {
        ArgumentOutOfRangeException.ThrowIfLessThan(maxRowsPerQuery, 1);
        ArgumentOutOfRangeException.ThrowIfLessThan(maxValuesPerInClause, 1);

        MaxRowsPerQuery = maxRowsPerQuery;
        MaxValuesPerInClause = maxValuesPerInClause;
    }

    public int MaxRowsPerQuery { get; }

    public int MaxValuesPerInClause { get; }
}
