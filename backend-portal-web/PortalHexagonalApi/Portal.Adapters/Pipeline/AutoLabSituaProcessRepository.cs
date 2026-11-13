using Portal.Adapters.AutoLab;
using Portal.Application.Pipeline;
using Portal.Application.Pipeline.Exceptions;
using Portal.Domain.Pipeline;

namespace Portal.Adapters.Pipeline;

public sealed class AutoLabSituaProcessRepository : ILaboratoryProgressRepository
{
    private readonly AutoLabDbContext _database;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabSituaProcessRepository(AutoLabDbContext database, AutoLabQueryLimits limits)
    {
        _database = database;
        _limits = limits;
    }

    public async Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(quoteCodes);

        string[] wanted = [.. quoteCodes
            .Where(code => !string.IsNullOrWhiteSpace(code))
            .Select(code => code.Trim())
            .Distinct(StringComparer.OrdinalIgnoreCase)];

        if (wanted.Length == 0) return [];

        List<SituaProcessRecord> rows = await AutoLabFetch.BoundedListInChunksAsync(
            wanted,
            _limits,
            (chunk, rowLimit) => AutoLabQueries.ProgressRowsOf(_database, chunk, rowLimit),
            Unavailable,
            cancellationToken);

        return SituaProcessReading.ProgressOf(rows.Select(ToRow), wanted);
    }

    public async Task<LaboratoryProgress?> GetProgressOfQuoteAsync(
        string quoteCode,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(quoteCode)) return null;

        IReadOnlyList<LaboratoryProgress> found = await GetProgressOfQuotesAsync(
            [quoteCode], cancellationToken);

        return found.FirstOrDefault();
    }

    public static SituaProcessRow ToRow(SituaProcessRecord record)
    {
        ArgumentNullException.ThrowIfNull(record);

        return new SituaProcessRow
        {
            OrcamentoCRM = record.OrcamentoCRM ?? string.Empty,
            Recebido = AsZeroOrOne(record.Recebido),
            Protocolado = AsZeroOrOne(record.Protocolado),
            SituacaoEnsaios = record.SituacaoEnsaios,
            SolicitacaoRelatorio = record.SolicitacaoRelatorio
        };
    }

    private static int? AsZeroOrOne(bool? flag)
    {
        return flag switch
        {
            true => 1,
            false => 0,
            null => null
        };
    }

    private static LaboratoryProgressSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new LaboratoryProgressSourceUnavailableException(message, cause);
    }
}
