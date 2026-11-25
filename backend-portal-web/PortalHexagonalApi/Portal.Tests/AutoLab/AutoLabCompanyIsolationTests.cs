using Portal.Adapters.AutoLab;
using Portal.Application.Paging;
using Portal.Application.ReportDocuments;
using Portal.Application.Visibility;
using Portal.Domain.LabResults;
using Portal.Domain.Pipeline;

using Xunit;

namespace Portal.Tests.AutoLab;

/// <summary>
/// Os casos em que os dados de duas empresas se parecem: a mesma serie, o mesmo nome, a mesma
/// identificacao de relatorio, o mesmo codigo de orcamento. Em nenhum deles o que decide e o
/// texto; decide o CNPJ do cliente da solicitacao.
/// </summary>
public sealed class AutoLabCompanyIsolationTests : IDisposable
{
    private const string Own = TestCompanies.Energy;
    private const string Other = TestCompanies.Distributor;

    private static readonly byte[] OwnPdf = [0x25, 0x50, 0x44, 0x46, 0x0A];
    private static readonly byte[] OtherPdf = [0x25, 0x50, 0x44, 0x46, 0x0B];

    private readonly AutoLabTestDatabase _database = new();

    public void Dispose()
    {
        _database.Dispose();
    }

    [Fact]
    public async Task TheSameSerialNumberInTwoCompaniesIsTwoEquipmentsAndEachSeesItsOwn()
    {
        _database.AddTestRow("P-1", "SERIE-IGUAL", Own, new DateTime(2026, 6, 1), identification: "LACTEC-1/2026");
        _database.AddTestRow("P-2", "SERIE-IGUAL", Other, new DateTime(2026, 7, 1), identification: "LACTEC-2/2026");

        Page<SampleAnalysis> listed = await _database.LabTests().GetAnalysesAsync(
            TestCompanies.ScopeOf(Own), null, PageRequest.First, CancellationToken.None);
        Page<SampleAnalysis> ofEquipment = await _database.LabTests().GetAnalysesOfEquipmentAsync(
            TestCompanies.ScopeOf(Own), "SERIE-IGUAL", PageRequest.First, CancellationToken.None);

        Assert.Equal(["P-1"], listed.Items.Select(analysis => analysis.ProtocoloLactec));
        Assert.Equal(1, listed.TotalCount);
        Assert.Equal(["P-1"], ofEquipment.Items.Select(analysis => analysis.ProtocoloLactec));
        Assert.Equal(1, ofEquipment.TotalCount);
        Assert.Null(await _database.LabTests().GetAnalysisByProtocolAsync(
            TestCompanies.ScopeOf(Own), "P-2", CancellationToken.None));
    }

    [Fact]
    public async Task TheCompanyNameWrittenInTheTestRowDecidesNothing()
    {
        _database.AddTestRow("P-1", "A", Own, new DateTime(2026, 6, 1));
        _database.AddUnlinkedTestRow("P-SEM-VINCULO", "B", Own, new DateTime(2026, 6, 2));
        _database.AddSample(_database.RequestOf(Other), "P-DO-OUTRO");
        _database.AddUnlinkedTestRow("P-DO-OUTRO", "C", Own, new DateTime(2026, 6, 3));

        Page<SampleAnalysis> listed = await _database.LabTests().GetAnalysesAsync(
            TestCompanies.ScopeOf(Own), null, PageRequest.First, CancellationToken.None);

        Assert.Equal(["A"], listed.Items.Select(analysis => analysis.EquipmentKey));
        Assert.Equal(1, listed.TotalCount);
        Assert.Empty((await _database.LabTests().GetAnalysesOfEquipmentAsync(
            TestCompanies.ScopeOf(Own), "B", PageRequest.First, CancellationToken.None)).Items);
        Assert.Null(await _database.LabTests().GetAnalysisByProtocolAsync(
            TestCompanies.ScopeOf(Own), "P-DO-OUTRO", CancellationToken.None));
        Assert.NotNull(await _database.LabTests().GetAnalysisByProtocolAsync(
            TestCompanies.ScopeOf(Other), "P-DO-OUTRO", CancellationToken.None));
    }

    [Fact]
    public async Task TheSearchDoesNotFindTheEquipmentOfAnotherCompany()
    {
        _database.AddTestRow("P-1", "TR-100", Own, new DateTime(2026, 6, 1));
        _database.AddTestRow("P-2", "TR-200", Other, new DateTime(2026, 6, 2));
        _database.AddEquipment("TR-200", "TAG-SECRETA", "138");

        foreach (string text in new[] { "TR-", "TR-200", "TAG-SECRETA", "LACTEC" })
        {
            Assert.True(Portal.Application.LabResults.EquipmentSearch.TryCreate(
                text, out Portal.Application.LabResults.EquipmentSearch? search));

            Page<SampleAnalysis> found = await _database.LabTests().GetAnalysesAsync(
                TestCompanies.ScopeOf(Own), search, PageRequest.First, CancellationToken.None);

            Assert.DoesNotContain(found.Items, analysis => analysis.EquipmentKey == "TR-200");
            Assert.True(found.TotalCount <= 1);
        }
    }

    [Fact]
    public async Task TheScopeWithoutProtocolsNeverTouchesTheTestView()
    {
        _database.AddTestRow("P-2", "TR-200", Other, new DateTime(2026, 6, 2));

        await _database.LabTests().GetAnalysesAsync(
            TestCompanies.ScopeOf(Own), null, PageRequest.First, CancellationToken.None);
        await _database.LabTests().GetAnalysesOfEquipmentAsync(
            TestCompanies.ScopeOf(Own), "TR-200", PageRequest.First, CancellationToken.None);
        await _database.LabTests().GetAnalysisByProtocolAsync(
            TestCompanies.ScopeOf(Own), "P-2", CancellationToken.None);
        await _database.LabTests().GetAnalysesAsync(
            CompanyScope.None, null, PageRequest.First, CancellationToken.None);

        Assert.DoesNotContain(_database.Commands, command =>
            command.Contains("vwDadosEnsaios", StringComparison.Ordinal));
    }

    [Fact]
    public async Task TheSameReportIdentificationInTwoCompaniesServesEachItsOwnFile()
    {
        Guid ownFile = _database.AddFile("proprio.pdf", OwnPdf);
        Guid otherFile = _database.AddFile("alheio.pdf", OtherPdf);

        _database.AddReportIssue("LACTEC-IGUAL/2026", ownFile, revision: 0, company: Own);
        _database.AddReportIssue("LACTEC-IGUAL/2026", otherFile, revision: 9, company: Other);
        _database.AddReportIssue("LACTEC-SO-DO-OUTRO/2026", otherFile, revision: 0, company: Other);

        AutoLabReportIssueRepositoryView own = new(_database, TestCompanies.ScopeOf(Own));

        Assert.Equal("proprio.pdf", (await own.SignedDocumentOfAsync("LACTEC-IGUAL/2026"))?.FileName);
        Assert.Null(await own.SignedDocumentOfAsync("LACTEC-SO-DO-OUTRO/2026"));
        Assert.Empty(await own.ReadingsOfAsync("LACTEC-SO-DO-OUTRO/2026"));
        Assert.Equal(
            ["LACTEC-IGUAL/2026"],
            await _database.ReportIssues().GetIdentificationsWithSignedDocumentAsync(
                TestCompanies.ScopeOf(Own),
                ["LACTEC-IGUAL/2026", "LACTEC-SO-DO-OUTRO/2026"],
                CancellationToken.None));
        Assert.Null(await new AutoLabReportIssueRepositoryView(_database, CompanyScope.None)
            .SignedDocumentOfAsync("LACTEC-IGUAL/2026"));
    }

    [Fact]
    public async Task TheReportOfAnotherCompanyIsNeitherListedNorDownloadedByItsId()
    {
        Guid ownFile = _database.AddFile("proprio.pdf", OwnPdf);
        Guid otherFile = _database.AddFile("alheio.pdf", OtherPdf);

        Guid ownReport = _database.AddReportIssue(
            "LACTEC-1/2026", ownFile, company: Own, quoteCode: "2026_00001", listed: true);
        Guid otherReport = _database.AddReportIssue(
            "LACTEC-2/2026", otherFile, company: Other, quoteCode: "2026_00001", listed: true);

        Page<LabReport> listed = await _database.ReportIssues().ListReportsAsync(
            TestCompanies.ScopeOf(Own), null, PageRequest.First, CancellationToken.None);
        Page<LabReport> ofQuote = await _database.ReportIssues().ListReportsAsync(
            TestCompanies.ScopeOf(Own), "2026_00001", PageRequest.First, CancellationToken.None);

        Assert.Equal([ownReport], listed.Items.Select(report => report.Id));
        Assert.Equal(1, listed.TotalCount);
        Assert.Equal([ownReport], ofQuote.Items.Select(report => report.Id));
        Assert.Equal("proprio.pdf", (await _database.ReportIssues().GetSignedDocumentOfReportAsync(
            TestCompanies.ScopeOf(Own), ownReport, CancellationToken.None))?.FileName);
        Assert.Null(await _database.ReportIssues().GetSignedDocumentOfReportAsync(
            TestCompanies.ScopeOf(Own), otherReport, CancellationToken.None));
        Assert.Null(await _database.ReportIssues().GetSignedDocumentOfReportAsync(
            CompanyScope.None, ownReport, CancellationToken.None));
    }

    [Fact]
    public async Task TheFileOfAnotherCompanyIsNeverReadFromTheDatabase()
    {
        Guid otherFile = _database.AddFile("alheio.pdf", OtherPdf);
        Guid otherReport = _database.AddReportIssue("LACTEC-2/2026", otherFile, company: Other, listed: true);

        await _database.ReportIssues().GetSignedDocumentOfReportAsync(
            TestCompanies.ScopeOf(Own), otherReport, CancellationToken.None);
        await _database.ReportIssues().GetSignedDocumentOfAsync(
            TestCompanies.ScopeOf(Own), "LACTEC-2/2026", CancellationToken.None);

        Assert.DoesNotContain(_database.Commands, command =>
            command.Contains("FileData", StringComparison.Ordinal));
    }

    [Fact]
    public async Task ADeletedIssueIsNotDownloadedByItsId()
    {
        Guid file = _database.AddFile("apagado.pdf", OwnPdf);
        Guid report = _database.AddReportIssue("LACTEC-1/2026", file, deleted: 7, company: Own, listed: true);

        Assert.Null(await _database.ReportIssues().GetSignedDocumentOfReportAsync(
            TestCompanies.ScopeOf(Own), report, CancellationToken.None));
        Assert.Empty((await _database.ReportIssues().ListReportsAsync(
            TestCompanies.ScopeOf(Own), null, PageRequest.First, CancellationToken.None)).Items);
    }

    [Fact]
    public async Task TheReportViewIsReadOneIssueAtATimeAndWithoutItsExpensiveColumns()
    {
        Guid file = _database.AddFile("proprio.pdf", OwnPdf);

        for (int index = 0; index < 3; index++)
            _database.AddReportIssue($"LACTEC-{index}/2026", file, company: Own, listed: true);

        await _database.ReportIssues().ListReportsAsync(
            TestCompanies.ScopeOf(Own), null, PageRequest.First, CancellationToken.None);

        string[] viewCommands = [.. _database.Commands
            .Where(command => command.Contains("vwRelatoriosEnsaios", StringComparison.Ordinal))];

        Assert.Equal(3, viewCommands.Length);
        Assert.All(viewCommands, command =>
        {
            Assert.Contains("\"Oid\" = ", command, StringComparison.Ordinal);
            Assert.DoesNotContain(" IN (", command, StringComparison.Ordinal);
            Assert.DoesNotContain("JOIN", command, StringComparison.Ordinal);
            Assert.DoesNotContain("\"Identificacao\"", command, StringComparison.Ordinal);
            Assert.DoesNotContain("\"DataPublicacao\"", command, StringComparison.Ordinal);
        });
    }

    [Fact]
    public async Task TheLaboratoryStageOfAQuoteCodeOfAnotherCompanyIsNotAnswered()
    {
        _database.AddSituaProcess("2026_00001", true, true, 3, 2, company: Own);
        _database.AddSituaProcess("2026_00002", true, true, 5, 2, company: Other);
        _database.AddSituaProcess("2026_00003", true, true, 5, 2, company: null);

        IReadOnlyList<LaboratoryProgress> found = await _database.Progress().GetProgressOfQuotesAsync(
            TestCompanies.ScopeOf(Own), ["2026_00001", "2026_00002", "2026_00003"], CancellationToken.None);

        Assert.Equal(["2026_00001"], found.Select(progress => progress.QuoteCode));
        Assert.Null(await _database.Progress().GetProgressOfQuoteAsync(
            TestCompanies.ScopeOf(Own), "2026_00002", CancellationToken.None));
        Assert.NotNull(await _database.Progress().GetProgressOfQuoteAsync(
            TestCompanies.ScopeOf(Other), "2026_00002", CancellationToken.None));
        Assert.Empty(await _database.Progress().GetProgressOfQuotesAsync(
            CompanyScope.None, ["2026_00001"], CancellationToken.None));
    }

    [Fact]
    public async Task TheStageViewIsNotAskedAboutCodesOutsideTheScope()
    {
        _database.AddSituaProcess("2026_00002", true, true, 5, 2, company: Other);

        await _database.Progress().GetProgressOfQuotesAsync(
            TestCompanies.ScopeOf(Own), ["2026_00002"], CancellationToken.None);

        Assert.DoesNotContain(_database.Commands, command =>
            command.Contains("SituaProcess", StringComparison.Ordinal));
    }

    private sealed class AutoLabReportIssueRepositoryView(AutoLabTestDatabase database, CompanyScope scope)
    {
        public Task<SignedReportDocument?> SignedDocumentOfAsync(string identification)
        {
            return database.ReportIssues().GetSignedDocumentOfAsync(scope, identification, CancellationToken.None);
        }

        public Task<IReadOnlyList<ReportReading>> ReadingsOfAsync(string identification)
        {
            return database.ReportIssues().ReadReportsAsync(scope, [identification], CancellationToken.None);
        }
    }
}
