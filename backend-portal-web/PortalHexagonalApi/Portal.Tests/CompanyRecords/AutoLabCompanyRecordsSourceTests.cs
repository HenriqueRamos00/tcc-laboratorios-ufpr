using Portal.Adapters.AutoLab;
using Portal.Adapters.CompanyRecords;
using Portal.Application.CompanyRecords;
using Portal.Application.CompanyRecords.Exceptions;
using Portal.Application.Visibility;
using Portal.Tests.AutoLab;

using Xunit;

namespace Portal.Tests.CompanyRecords;

/// <summary>
/// O caminho do CNPJ ate o registro, contra o esquema do AutoLAB: <c>Cliente</c>,
/// <c>SolicitacaoServico</c>, <c>AmostraObjeto</c>, <c>ProtocoloAmostra</c> e
/// <c>RelatorioEmissao.Solicitacao</c>.
/// </summary>
public sealed class AutoLabCompanyRecordsSourceTests : IDisposable
{
    private const string OwnCnpj = "30960572000183";
    private const string OtherCnpj = "11222333000181";
    private const int Deleted = 1873465;

    private static readonly CompanyScope Own = CompanyScope.OfCnpjs([OwnCnpj]);
    private static readonly CompanyScope Other = CompanyScope.OfCnpjs([OtherCnpj]);

    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Theory]
    [InlineData("30.960.572/0001-83")]
    [InlineData("30960572000183")]
    [InlineData(" 30.960.572/0001-83 ")]
    [InlineData("30 960 572/0001-83")]
    public async Task TheClientIsFoundByTheCnpjWhateverTheMaskItWasStoredWith(string stored)
    {
        Guid request = _database.AddRequest(_database.AddClient(stored), quoteCode: "2026_00001");
        _database.AddSample(request, "P-1");

        Assert.Equal(["P-1"], await Source().GetProtocolsAsync(Own, CancellationToken.None));
        Assert.Equal(["2026_00001"], await Source().GetQuoteCodesAsync(Own, CancellationToken.None));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("309.605.720-00")]
    [InlineData("30.960.572/0001-8")]
    [InlineData("30.960.572/0001-833")]
    [InlineData("30.960.572/0001-83 FILIAL")]
    [InlineData("11.222.333/0001-81")]
    public async Task AClientWhoseStoredValueIsNotThatCnpjOwnsNothingOfTheScope(string? stored)
    {
        Guid request = _database.AddRequest(_database.AddClient(stored), quoteCode: "2026_00001");
        _database.AddSample(request, "P-1");
        AddIssue(request, "LACTEC-00001/2026");

        Assert.Empty(await Source().GetProtocolsAsync(Own, CancellationToken.None));
        Assert.Empty(await Source().GetReportsAsync(Own, CancellationToken.None));
        Assert.Empty(await Source().GetQuoteCodesAsync(Own, CancellationToken.None));
    }

    [Fact]
    public async Task EachCompanyReachesOnlyItsOwnProtocolsReportsAndQuoteCodes()
    {
        Guid ownRequest = _database.AddRequest(_database.AddClient("30.960.572/0001-83"), "SOL-1", "2026_00001");
        Guid otherRequest = _database.AddRequest(_database.AddClient("11.222.333/0001-81"), "SOL-2", "2026_00002");
        _database.AddSample(ownRequest, "P-1");
        _database.AddSample(otherRequest, "P-2");
        Guid ownIssue = AddIssue(ownRequest, "LACTEC-00001/2026");
        Guid otherIssue = AddIssue(otherRequest, "LACTEC-00002/2026");

        Assert.Equal(["P-1"], await Source().GetProtocolsAsync(Own, CancellationToken.None));
        Assert.Equal(["P-2"], await Source().GetProtocolsAsync(Other, CancellationToken.None));
        Assert.Equal([ownIssue], (await Source().GetReportsAsync(Own, CancellationToken.None)).Select(report => report.Id));
        Assert.Equal([otherIssue], (await Source().GetReportsAsync(Other, CancellationToken.None)).Select(report => report.Id));
        Assert.Equal(["2026_00001"], await Source().GetQuoteCodesAsync(Own, CancellationToken.None));
        Assert.Equal(["2026_00002"], await Source().GetQuoteCodesAsync(Other, CancellationToken.None));
        Assert.Equal(
            ["P-1", "P-2"],
            (await Source().GetProtocolsAsync(CompanyScope.OfCnpjs([OwnCnpj, OtherCnpj]), CancellationToken.None))
                .Order(StringComparer.Ordinal));
    }

    [Fact]
    public async Task TheSameRequestNumberInTwoCompaniesDoesNotJoinThem()
    {
        Guid ownRequest = _database.AddRequest(_database.AddClient("30.960.572/0001-83"), "SOL-DUPLICADA", "2026_00001");
        Guid otherRequest = _database.AddRequest(_database.AddClient("11.222.333/0001-81"), "SOL-DUPLICADA", "2026_00002");
        _database.AddSample(ownRequest, "P-1");
        _database.AddSample(otherRequest, "P-2");
        Guid ownIssue = AddIssue(ownRequest, "LACTEC-00001/2026");
        AddIssue(otherRequest, "LACTEC-00002/2026");

        CompanyReportRow report = Assert.Single(await Source().GetReportsAsync(Own, CancellationToken.None));

        Assert.Equal(ownIssue, report.Id);
        Assert.Equal("SOL-DUPLICADA", report.RequestNumber);
        Assert.Equal("2026_00001", report.QuoteCode);
        Assert.Equal(["P-1"], await Source().GetProtocolsAsync(Own, CancellationToken.None));
    }

    [Fact]
    public async Task TheSameRequestNumberTwiceInOneCompanyDoesNotDuplicateRows()
    {
        Guid client = _database.AddClient("30.960.572/0001-83");
        Guid first = _database.AddRequest(client, "SOL-DUPLICADA", "2026_00001");
        Guid second = _database.AddRequest(client, "SOL-DUPLICADA", "2026_00001");
        _database.AddSample(first, "P-1");
        _database.AddSample(second, "P-1");
        Guid issue = AddIssue(first, "LACTEC-00001/2026");

        Assert.Equal(["P-1"], await Source().GetProtocolsAsync(Own, CancellationToken.None));
        Assert.Equal([issue], (await Source().GetReportsAsync(Own, CancellationToken.None)).Select(report => report.Id));
        Assert.Equal(["2026_00001"], await Source().GetQuoteCodesAsync(Own, CancellationToken.None));
    }

    [Fact]
    public async Task TwoClientRowsWithTheSameCnpjAreTheSameCompany()
    {
        _database.AddSample(_database.AddRequest(_database.AddClient("30.960.572/0001-83")), "P-1");
        _database.AddSample(_database.AddRequest(_database.AddClient("30960572000183")), "P-2");

        Assert.Equal(
            ["P-1", "P-2"],
            (await Source().GetProtocolsAsync(Own, CancellationToken.None)).Order(StringComparer.Ordinal));
    }

    [Fact]
    public async Task ADeletedClientOwnsNothing()
    {
        Guid request = _database.AddRequest(_database.AddClient("30.960.572/0001-83", Deleted), quoteCode: "2026_00001");
        _database.AddSample(request, "P-1");
        AddIssue(request, "LACTEC-00001/2026");

        await AssertNothingIsReachedAsync();
    }

    [Fact]
    public async Task ADeletedRequestCarriesNothing()
    {
        Guid request = _database.AddRequest(
            _database.AddClient("30.960.572/0001-83"), quoteCode: "2026_00001", deleted: Deleted);
        _database.AddSample(request, "P-1");
        AddIssue(request, "LACTEC-00001/2026");

        await AssertNothingIsReachedAsync();
    }

    [Fact]
    public async Task ADeletedSampleADeletedProtocolAndADeletedIssueAreNotReached()
    {
        Guid request = _database.AddRequest(_database.AddClient("30.960.572/0001-83"));
        _database.AddSample(request, "P-AMOSTRA-APAGADA", sampleDeleted: Deleted);
        _database.AddSample(request, "P-PROTOCOLO-APAGADO", protocolDeleted: Deleted);
        _database.AddSample(request, null);
        _database.AddSample(request, "P-VIVO");
        AddIssue(request, "LACTEC-APAGADO/2026", deleted: Deleted);
        Guid live = AddIssue(request, "LACTEC-VIVO/2026");

        Assert.Equal(["P-VIVO"], await Source().GetProtocolsAsync(Own, CancellationToken.None));
        Assert.Equal([live], (await Source().GetReportsAsync(Own, CancellationToken.None)).Select(report => report.Id));
    }

    [Fact]
    public async Task ASampleWithoutRequestAndAnIssueWithoutRequestBelongToNoCompany()
    {
        _database.AddClient("30.960.572/0001-83");
        _database.AddSample(null, "P-ORFAO");
        _database.AddReportIssue("LACTEC-ORFAO/2026", signedPdf: null, company: null);
        _database.AddRequest(null, quoteCode: "2026_00009");

        await AssertNothingIsReachedAsync();
    }

    [Fact]
    public async Task TheEmptyScopeAsksTheDatabaseNothing()
    {
        _database.AddSample(_database.AddRequest(_database.AddClient("30.960.572/0001-83")), "P-1");

        AutoLabCompanyRecordsSource source = Source();

        Assert.Empty(await source.GetProtocolsAsync(CompanyScope.None, CancellationToken.None));
        Assert.Empty(await source.GetReportsAsync(CompanyScope.None, CancellationToken.None));
        Assert.Empty(await source.GetQuoteCodesAsync(CompanyScope.None, CancellationToken.None));
        Assert.Empty(_database.Commands);
    }

    [Fact]
    public async Task ACompanyWithoutClientRowDoesNotReachTheOtherTables()
    {
        _database.AddSample(_database.AddRequest(_database.AddClient("11.222.333/0001-81")), "P-2");

        Assert.Empty(await Source().GetProtocolsAsync(Own, CancellationToken.None));

        string onlyCommand = Assert.Single(_database.Commands);
        Assert.Contains("\"Cliente\"", onlyCommand, StringComparison.Ordinal);
        Assert.DoesNotContain("SolicitacaoServico", onlyCommand, StringComparison.Ordinal);
    }

    [Fact]
    public async Task OnlyTablesAreReadAndTheCompanyIsResolvedOncePerRequest()
    {
        Guid request = _database.AddRequest(_database.AddClient("30.960.572/0001-83"), quoteCode: "2026_00001");
        _database.AddSample(request, "P-1");
        AddIssue(request, "LACTEC-00001/2026");

        AutoLabCompanyRecordsSource source = Source();

        await source.GetProtocolsAsync(Own, CancellationToken.None);
        await source.GetReportsAsync(Own, CancellationToken.None);
        await source.GetQuoteCodesAsync(Own, CancellationToken.None);
        await source.GetProtocolsAsync(Own, CancellationToken.None);
        await source.GetReportsAsync(CompanyScope.OfCnpjs([OwnCnpj]), CancellationToken.None);

        Assert.Equal(4, _database.Commands.Count);
        Assert.Single(_database.Commands, command => command.Contains("cli_cadastronacional_a", StringComparison.Ordinal));
        Assert.All(_database.Commands, command =>
        {
            Assert.DoesNotContain("vwDadosEnsaios", command, StringComparison.Ordinal);
            Assert.DoesNotContain("vwRelatoriosEnsaios", command, StringComparison.Ordinal);
            Assert.Contains("WHERE", command, StringComparison.Ordinal);
            Assert.Contains("LIMIT", command, StringComparison.Ordinal);
        });
    }

    [Fact]
    public async Task TheCnpjTravelsAsAParameterAndNeverInsideTheCommandText()
    {
        _database.AddSample(_database.AddRequest(_database.AddClient("30.960.572/0001-83")), "P-1");

        await Source().GetProtocolsAsync(Own, CancellationToken.None);

        Assert.All(_database.Commands, command =>
            Assert.DoesNotContain(OwnCnpj, command, StringComparison.Ordinal));
    }

    [Fact]
    public async Task MoreRecordsThanTheLimitFailLoudlyInsteadOfServingAPartOfThem()
    {
        Guid request = _database.AddRequest(_database.AddClient("30.960.572/0001-83"));

        for (int index = 0; index < 4; index++) _database.AddSample(request, $"P-{index}");

        AutoLabCompanyRecordsSource source = _database.CompanyRecords(
            _database.NewContext(), new AutoLabQueryLimits(maxRowsPerQuery: 3));

        await Assert.ThrowsAsync<CompanyRecordsSourceUnavailableException>(
            () => source.GetProtocolsAsync(Own, CancellationToken.None));
    }

    [Fact]
    public async Task ABrokenDatabaseIsAFailureAndNeverAnEmptyOrAWiderAnswer()
    {
        AutoLabTestDatabase broken = new();
        AutoLabDbContext context = broken.NewContext();
        broken.Dispose();

        await Assert.ThrowsAsync<CompanyRecordsSourceUnavailableException>(
            () => broken.CompanyRecords(context).GetProtocolsAsync(Own, CancellationToken.None));
    }

    private async Task AssertNothingIsReachedAsync()
    {
        Assert.Empty(await Source().GetProtocolsAsync(Own, CancellationToken.None));
        Assert.Empty(await Source().GetReportsAsync(Own, CancellationToken.None));
        Assert.Empty(await Source().GetQuoteCodesAsync(Own, CancellationToken.None));
    }

    private Guid AddIssue(Guid request, string identification, int? deleted = null)
    {
        return _database.AddReportIssueOfRequest(request, identification, deleted);
    }

    private AutoLabCompanyRecordsSource Source()
    {
        return _database.CompanyRecords(_database.NewContext());
    }
}
