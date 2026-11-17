using Portal.Adapters.AutoLab;
using Portal.Application.CompanyRecords;
using Portal.Application.CompanyRecords.Exceptions;
using Portal.Application.Pipeline;
using Portal.Application.Pipeline.Exceptions;
using Portal.Application.Visibility;
using Portal.Domain.Pipeline;

namespace Portal.Adapters.Pipeline;

/// <summary>
/// <c>SituaProcess</c> nao tem coluna de empresa. O escopo entra antes da consulta: so sao
/// perguntados os codigos de orcamento que pertencem a uma solicitacao das empresas do
/// escopo, segundo <see cref="ICompanyRecordsSource"/>.
/// </summary>
public sealed class AutoLabSituaProcessRepository : ILaboratoryProgressRepository
{
    private readonly AutoLabDbContext _database;
    private readonly ICompanyRecordsSource _companyRecords;
    private readonly AutoLabQueryLimits _limits;

    public AutoLabSituaProcessRepository(
        AutoLabDbContext database,
        ICompanyRecordsSource companyRecords,
        AutoLabQueryLimits limits)
    {
        _database = database;
        _companyRecords = companyRecords;
        _limits = limits;
    }

    public async Task<IReadOnlyList<LaboratoryProgress>> GetProgressOfQuotesAsync(
        CompanyScope scope,
        IReadOnlyCollection<string> quoteCodes,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);
        ArgumentNullException.ThrowIfNull(quoteCodes);

        string[] asked = [.. quoteCodes
            .Where(code => !string.IsNullOrWhiteSpace(code))
            .Select(code => code.Trim())
            .Distinct(StringComparer.OrdinalIgnoreCase)];

        if (asked.Length == 0) return [];

        HashSet<string> authorized = new(
            await AuthorizedQuoteCodesAsync(scope, cancellationToken), StringComparer.OrdinalIgnoreCase);

        string[] wanted = [.. asked.Where(authorized.Contains)];

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
        CompanyScope scope,
        string quoteCode,
        CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(quoteCode)) return null;

        IReadOnlyList<LaboratoryProgress> found = await GetProgressOfQuotesAsync(
            scope, [quoteCode], cancellationToken);

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

    private async Task<IReadOnlyCollection<string>> AuthorizedQuoteCodesAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        try
        {
            return await _companyRecords.GetQuoteCodesAsync(scope, cancellationToken);
        }
        catch (CompanyRecordsSourceUnavailableException exception)
        {
            throw Unavailable(exception.Message, exception);
        }
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
