using Portal.Adapters.AutoLab;
using Portal.Application.CompanyRecords;
using Portal.Application.CompanyRecords.Exceptions;
using Portal.Application.Visibility;

namespace Portal.Adapters.CompanyRecords;

/// <summary>
/// O unico adaptador de <see cref="ICompanyRecordsSource"/>, e o unico lugar do portal que
/// atravessa as tabelas do AutoLAB do CNPJ ate o registro: <c>Cliente</c> pelo CNPJ sem
/// mascara, <c>SolicitacaoServico</c> pelo cliente, e dai <c>AmostraObjeto</c> e
/// <c>ProtocoloAmostra</c> para os protocolos e <c>RelatorioEmissao.Solicitacao</c> para as
/// emissoes. So tabelas entram nestas consultas; as views sao lidas depois, pelos outros
/// adaptadores, com as chaves que sairam daqui.
///
/// Os clientes sao resolvidos primeiro, numa consulta pequena, e conferidos de novo na
/// memoria com a mesma normalizacao do escopo: a comparacao do banco depende da collation, e
/// a da aplicacao nao. Linha com <c>GCRecord</c> preenchido nao existe para o portal em
/// nenhuma das tabelas.
///
/// A instancia vive o tempo de uma requisicao e lembra o que ja respondeu para o mesmo
/// escopo, porque a aplicacao pergunta varias vezes por tela.
///
/// Quando houver uma view do portal com a coluna de CNPJ, esta classe e a que muda: cada
/// metodo vira um <c>SELECT ... FROM dbo.vwPortal... WHERE Cnpj IN (...)</c>.
/// </summary>
public sealed class AutoLabCompanyRecordsSource : ICompanyRecordsSource
{
    private readonly AutoLabDbContext _database;
    private readonly AutoLabQueryLimits _limits;
    private readonly Dictionary<CompanyScope, Dictionary<Guid, string>> _clients = [];
    private readonly Dictionary<CompanyScope, IReadOnlyCollection<string>> _protocols = [];
    private readonly Dictionary<CompanyScope, IReadOnlyList<CompanyReportRow>> _reports = [];
    private readonly Dictionary<CompanyScope, IReadOnlyCollection<string>> _quoteCodes = [];

    public AutoLabCompanyRecordsSource(AutoLabDbContext database, AutoLabQueryLimits limits)
    {
        _database = database;
        _limits = limits;
    }

    public async Task<IReadOnlyCollection<string>> GetProtocolsAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (_protocols.TryGetValue(scope, out IReadOnlyCollection<string>? known)) return known;

        List<Guid?> clients = await ClientsOfAsync(scope, cancellationToken);

        IReadOnlyCollection<string> protocols = clients.Count == 0
            ? []
            : [.. (await AutoLabFetch.BoundedListInChunksAsync(
                    clients,
                    _limits,
                    (chunk, rowLimit) => AutoLabQueries.ProtocolsOfClients(_database, chunk, rowLimit),
                    Unavailable,
                    cancellationToken))
                .Distinct(StringComparer.Ordinal)];

        _protocols[scope] = protocols;

        return protocols;
    }

    public async Task<IReadOnlyList<CompanyReportRow>> GetReportsAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (_reports.TryGetValue(scope, out IReadOnlyList<CompanyReportRow>? known)) return known;

        Dictionary<Guid, string> cnpjOfClient = await CnpjOfClientsAsync(scope, cancellationToken);

        IReadOnlyList<CompanyReportRow> reports = cnpjOfClient.Count == 0
            ? []
            : [.. (await AutoLabFetch.BoundedListInChunksAsync(
                    [.. cnpjOfClient.Keys.Select(client => (Guid?)client)],
                    _limits,
                    (chunk, rowLimit) => AutoLabQueries.ReportsOfClients(_database, chunk, rowLimit),
                    Unavailable,
                    cancellationToken))
                .DistinctBy(found => found.Report.Id)
                .Select(found => found.Report with
                {
                    Cnpj = found.ClientId is Guid client ? cnpjOfClient.GetValueOrDefault(client) : null
                })];

        _reports[scope] = reports;

        return reports;
    }

    public async Task<IReadOnlyCollection<string>> GetQuoteCodesAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(scope);

        if (_quoteCodes.TryGetValue(scope, out IReadOnlyCollection<string>? known)) return known;

        List<Guid?> clients = await ClientsOfAsync(scope, cancellationToken);

        IReadOnlyCollection<string> quoteCodes = clients.Count == 0
            ? []
            : [.. (await AutoLabFetch.BoundedListInChunksAsync(
                    clients,
                    _limits,
                    (chunk, rowLimit) => AutoLabQueries.QuoteCodesOfClients(_database, chunk, rowLimit),
                    Unavailable,
                    cancellationToken))
                .Where(code => !string.IsNullOrWhiteSpace(code))
                .Select(code => code.Trim())
                .Distinct(StringComparer.OrdinalIgnoreCase)];

        _quoteCodes[scope] = quoteCodes;

        return quoteCodes;
    }

    private async Task<List<Guid?>> ClientsOfAsync(CompanyScope scope, CancellationToken cancellationToken)
    {
        return [.. (await CnpjOfClientsAsync(scope, cancellationToken)).Keys.Select(client => (Guid?)client)];
    }

    private async Task<Dictionary<Guid, string>> CnpjOfClientsAsync(
        CompanyScope scope,
        CancellationToken cancellationToken)
    {
        if (_clients.TryGetValue(scope, out Dictionary<Guid, string>? known)) return known;

        Dictionary<Guid, string> clients = [];

        if (!scope.IsEmpty)
        {
            List<ClienteRow> candidates = await AutoLabFetch.BoundedListInChunksAsync(
                scope.Cnpjs,
                _limits,
                (chunk, rowLimit) => AutoLabQueries.ClientsOfCnpjs(_database, chunk, rowLimit),
                Unavailable,
                cancellationToken);

            foreach (ClienteRow candidate in candidates.Where(candidate => scope.Includes(candidate.CadastroNacional)))
                clients.TryAdd(candidate.Oid, CompanyScope.Normalize(candidate.CadastroNacional)!);
        }

        _clients[scope] = clients;

        return clients;
    }

    private static CompanyRecordsSourceUnavailableException Unavailable(string message, Exception cause)
    {
        return new CompanyRecordsSourceUnavailableException(message, cause);
    }
}
