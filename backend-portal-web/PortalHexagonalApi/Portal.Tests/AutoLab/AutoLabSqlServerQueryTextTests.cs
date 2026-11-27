using System.Text.RegularExpressions;

using Microsoft.EntityFrameworkCore;

using Portal.Adapters.AutoLab;
using Portal.Api.AutoLab;
using Portal.Application.LabResults;
using Portal.Application.Paging;

using Xunit;
using Xunit.Abstractions;

namespace Portal.Tests.AutoLab;

public sealed class AutoLabSqlServerQueryTextTests
{
    private readonly ITestOutputHelper _output;

    public AutoLabSqlServerQueryTextTests(ITestOutputHelper output)
    {
        _output = output;
    }

    private static readonly string[] Names =
    [
        "ClientsOfCnpjs",
        "ProtocolsOfClients",
        "ReportsOfClients",
        "SignedReportsOfClients",
        "ProtocolSummaries",
        "ReportsOfProtocols",
        "QuoteCodesOfClients",
        "EquipmentKeys",
        "EquipmentKeysOfSearch",
        "RowsOfEquipmentKeys",
        "ProtocolsOfEquipment",
        "RowsOfProtocols",
        "RowsOfProtocol",
        "RegistryRowsOf",
        "ProgressRowsOf",
        "ReportViewRowOf",
        "LiveFilesAmong",
        "LiveFileOf"
    ];

    public static TheoryData<string> QueryNames => new(Names);

    [Theory]
    [MemberData(nameof(QueryNames))]
    public void EveryQueryHasAFilterAndARowLimitInTheDatabase(string name)
    {
        string sql = SqlOf(name);

        _output.WriteLine(sql);

        Assert.Contains("WHERE", sql, StringComparison.Ordinal);
        Assert.True(
            sql.Contains("TOP(", StringComparison.Ordinal)
                || sql.Contains("FETCH NEXT", StringComparison.Ordinal),
            sql);
        Assert.DoesNotContain("PdfLiberado", sql, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("EquipmentKeys")]
    [InlineData("EquipmentKeysOfSearch")]
    [InlineData("RowsOfEquipmentKeys")]
    [InlineData("ProtocolsOfEquipment")]
    [InlineData("RowsOfProtocols")]
    [InlineData("RowsOfProtocol")]
    [InlineData("RegistryRowsOf")]
    public void TheIdentityComparisonsCarryTheOrdinalCollation(string name)
    {
        Assert.Contains(
            "COLLATE Latin1_General_BIN2",
            SqlOf(name),
            StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("RowsOfEquipmentKeys", "NumSerie")]
    [InlineData("RowsOfEquipmentKeys", "ProtocoloLactec")]
    [InlineData("ProtocolsOfEquipment", "NumSerie")]
    [InlineData("ProtocolsOfEquipment", "ProtocoloLactec")]
    [InlineData("RowsOfProtocols", "ProtocoloLactec")]
    [InlineData("RowsOfProtocol", "ProtocoloLactec")]
    [InlineData("RegistryRowsOf", "NumSerie")]
    public void TheIdentityLookupsKeepAComparisonOnTheBareColumn(string name, string column)
    {
        Assert.Matches(
            new Regex(@"\[\w+\]\.\[" + column + @"\] (=|IN) "),
            SqlOf(name));
    }

    [Theory]
    [InlineData("EquipmentKeys")]
    [InlineData("EquipmentKeysOfSearch")]
    [InlineData("ProtocolsOfEquipment")]
    public void ThePagedKeysUseADeterministicOffsetFetch(string name)
    {
        string sql = SqlOf(name);

        Assert.Contains("ORDER BY", sql, StringComparison.Ordinal);
        Assert.Contains("OFFSET", sql, StringComparison.Ordinal);
        Assert.Contains("FETCH NEXT", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void TheSearchIsAnEscapedCaseInsensitiveLikeOverTheThreeColumns()
    {
        string sql = SqlOf("EquipmentKeysOfSearch");

        _output.WriteLine(sql);

        const string like = @" COLLATE Latin1_General_CI_AS LIKE @\w+ ESCAPE N'\\'";

        Assert.Matches(new Regex(@"\[\w+\]\.\[NumSerie\]" + like), sql);
        Assert.Matches(new Regex(@"\[\w+\]\.\[Identificacao\]" + like), sql);
        Assert.Matches(new Regex(@"\[\w+\]\.\[Tag\]" + like), sql);
        Assert.Equal(3, Regex.Count(sql, @"ESCAPE N'\\'"));
        Assert.Matches(new Regex(@"LEFT JOIN \(\s*SELECT DISTINCT [^\n]+\s+FROM \[dbo\]\.\[Equipamentos\]"), sql);
        Assert.DoesNotContain("EXISTS", sql, StringComparison.Ordinal);
        Assert.Equal(1, Regex.Count(sql, @"\[dbo\]\.\[vwDadosEnsaios\]"));
        Assert.Matches(new Regex(@"HAVING [^\n]*MAX\(CASE"), sql);
        Assert.DoesNotContain("[EmpresaTrafo] =", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("CHARINDEX", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("TR[_]%", sql, StringComparison.Ordinal);
        Assert.Contains(@"%TR\[\_\%\\%", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void ThePatternOfTheLongestSearchIsNotCutToTheSizeOfTheColumn()
    {
        string sql = SqlOf("EquipmentKeysOfLongestSearch");

        string pattern = "%" + string.Concat(Enumerable.Repeat(@"\_", EquipmentSearch.MaxLength)) + "%";

        Assert.Contains("nvarchar(4000) = N'" + pattern + "'", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("nvarchar(100) = N'%", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void TheListingWithoutSearchDoesNotTouchTheRegistryNorUseLike()
    {
        string sql = SqlOf("EquipmentKeys");

        Assert.DoesNotContain("LIKE", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("Equipamentos", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void OnlyTheDocumentQueryReadsTheBinaryContent()
    {
        foreach (string name in Names)
        {
            bool readsContent = SqlOf(name).Contains("[Content]", StringComparison.Ordinal);

            Assert.Equal(name is "LiveFileOf", readsContent);
        }
    }

    [Fact]
    public void TheConnectionStringCarriesTheProtectiveLimitsAndNoSecretInTheCode()
    {
        AutoLabOptions options = new()
        {
            Server = "LACTECSQL10,49933",
            Database = "bdautolab_ensaios",
            User = "projeto_portal",
            Password = "x;y"
        };

        Microsoft.Data.SqlClient.SqlConnectionStringBuilder built = new(options.BuildConnectionString());

        Assert.Empty(options.Problems());
        Assert.Equal("LACTECSQL10,49933", built.DataSource);
        Assert.Equal("x;y", built.Password);
        Assert.Equal(Microsoft.Data.SqlClient.SqlConnectionEncryptOption.Optional, built.Encrypt);
        Assert.True(built.TrustServerCertificate);
        Assert.Equal(10, built.MaxPoolSize);
        Assert.Equal(10, built.CommandTimeout);
        Assert.Equal(10, built.ConnectTimeout);
        Assert.Equal(Microsoft.Data.SqlClient.ApplicationIntent.ReadOnly, built.ApplicationIntent);
        Assert.False(built.PersistSecurityInfo);
    }

    [Fact]
    public void MissingSettingsAreNamedWithoutRevealingValues()
    {
        AutoLabOptions options = new() { Password = "segredo", MaxPoolSize = 0 };

        string[] problems = [.. options.Problems()];

        Assert.Contains("AutoLab:Server", problems);
        Assert.Contains("AutoLab:Database", problems);
        Assert.Contains("AutoLab:User", problems);
        Assert.DoesNotContain("AutoLab:Password", problems);
        Assert.Contains(problems, problem => problem.StartsWith("AutoLab:MaxPoolSize", StringComparison.Ordinal));
        Assert.DoesNotContain(problems, problem => problem.Contains("segredo", StringComparison.Ordinal));
    }

    [Fact]
    public void TheClientIsFoundByTheStoredCnpjWithoutItsMaskAndTheCnpjTravelsAsAParameter()
    {
        string sql = SqlOf("ClientsOfCnpjs");

        _output.WriteLine(sql);

        Assert.Contains(
            "REPLACE(REPLACE(REPLACE(REPLACE([c].[cli_cadastronacional_a], N'.', N''), N'/', N''), N'-', N''), N' ', N'')",
            sql,
            StringComparison.Ordinal);
        Assert.Contains("[c].[GCRecord] IS NULL", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("30960572000183", sql[sql.IndexOf("SELECT", StringComparison.Ordinal)..], StringComparison.Ordinal);
        Assert.DoesNotContain("LIKE", sql, StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("ClientsOfCnpjs")]
    [InlineData("ProtocolsOfClients")]
    [InlineData("ReportsOfClients")]
    [InlineData("SignedReportsOfClients")]
    [InlineData("QuoteCodesOfClients")]
    public void TheCompanyRecordsAreResolvedOnTablesOnlyAndIgnoreDeletedRows(string name)
    {
        string sql = SqlOf(name);

        _output.WriteLine(sql);

        Assert.DoesNotContain("vwDadosEnsaios", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("vwRelatoriosEnsaios", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("SituaProcess", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[Content]", sql, StringComparison.Ordinal);
        Assert.Equal(
            Regex.Count(sql, @"(FROM|JOIN) \[dbo\]\.\["),
            Regex.Count(sql, @"\[GCRecord\] IS NULL"));
    }

    [Fact]
    public void TheProtocolsComeFromTheSampleOfTheRequestOfTheClient()
    {
        string sql = SqlOf("ProtocolsOfClients");

        Assert.Contains("[dbo].[SolicitacaoServico]", sql, StringComparison.Ordinal);
        Assert.Contains("[dbo].[AmostraObjeto]", sql, StringComparison.Ordinal);
        Assert.Contains("[dbo].[ProtocoloAmostra]", sql, StringComparison.Ordinal);
        Assert.Matches(new Regex(@"\[\w+\]\.\[Cliente\] (=|IN) "), sql);
        Assert.Contains("DISTINCT", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void TheReportsComeFromTheIssueJoinedToTheRequestByItsKeyAndNeverByItsNumber()
    {
        string sql = SqlOf("ReportsOfClients");

        Assert.Matches(new Regex(@"\[\w+\]\.\[Solicitacao\] = \[\w+\]\.\[Oid\]"), sql);
        Assert.DoesNotMatch(new Regex(@"ON [^\n]*NumeroSolicitacao"), sql);
    }

    [Fact]
    public void TheSignedReportsOfTheLoadComeInIssueOrderFromACursorAndNeverBringTheFile()
    {
        string sql = SqlOf("SignedReportsOfClients");

        _output.WriteLine(sql);

        Assert.Contains("[PdfAssinado] IS NOT NULL", sql, StringComparison.Ordinal);
        Assert.Contains("[DataEmissao] >=", sql, StringComparison.Ordinal);
        Assert.Matches(new Regex(@"ORDER BY \[\w+\]\.\[DataEmissao\], \[\w+\]\.\[Oid\]"), sql);
        Assert.Matches(new Regex(@"\[\w+\]\.\[Oid\] > "), sql);
        Assert.DoesNotContain("FileData", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void TheProtocolSummariesBringOneRowPerProtocolAndNeverTheMeasurements()
    {
        string sql = SqlOf("ProtocolSummaries");

        _output.WriteLine(sql);

        Assert.Contains("DISTINCT", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[Resultado]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[Name]", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void TheReportsOfTheProtocolsComeGroupedInTheDatabaseAndNeverBringTheMeasurements()
    {
        string sql = SqlOf("ReportsOfProtocols");

        _output.WriteLine(sql);

        Assert.Matches(new Regex(@"WHERE \[\w+\]\.\[ProtocoloLactec\] IN \("), sql);
        Assert.Matches(new Regex(@"GROUP BY \[\w+\]\.\[ProtocoloLactec\], \[\w+\]\.\[Identificacao\]"), sql);
        Assert.Matches(new Regex(@"MAX\(\[\w+\]\.\[DataEmissao\]\)"), sql);
        Assert.Contains("TOP(", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[Resultado]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[Name]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("JOIN", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void TheReportViewIsAskedForOneIssueByItsKeyAndNeverForTheExpensiveColumns()
    {
        string sql = SqlOf("ReportViewRowOf");

        _output.WriteLine(sql);

        Assert.Matches(new Regex(@"WHERE \[\w+\]\.\[Oid\] = @"), sql);
        Assert.DoesNotContain(" IN ", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("JOIN", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[Identificacao]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[DataPublicacao]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[DataEmissao]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("[PdfAssinado]", sql, StringComparison.Ordinal);
        Assert.DoesNotContain("ORDER BY", sql, StringComparison.Ordinal);
    }

    [Fact]
    public void NoOtherQueryTouchesTheReportView()
    {
        foreach (string name in Names.Where(name => name != "ReportViewRowOf"))
            Assert.DoesNotContain("vwRelatoriosEnsaios", SqlOf(name), StringComparison.Ordinal);
    }

    [Theory]
    [InlineData("EquipmentKeys")]
    [InlineData("EquipmentKeysOfSearch")]
    [InlineData("RowsOfEquipmentKeys")]
    [InlineData("ProtocolsOfEquipment")]
    [InlineData("RowsOfProtocols")]
    [InlineData("RowsOfProtocol")]
    public void EveryReadingOfTheTestViewIsRestrictedToTheAuthorizedProtocols(string name)
    {
        string sql = SqlOf(name);

        Assert.Matches(new Regex(@"\[\w+\]\.\[ProtocoloLactec\] IN \("), sql);
        Assert.DoesNotMatch(new Regex(@"\[EmpresaTrafo\]\)* (=|IN|LIKE) "), sql);
        Assert.DoesNotMatch(new Regex(@"COLLATE \w+ = @\w*company", RegexOptions.IgnoreCase), sql);
    }

    [Fact]
    public void ACompanyWithThousandsOfProtocolsStillFitsInOneCommand()
    {
        using AutoLabDbContext database = new(new DbContextOptionsBuilder<AutoLabDbContext>()
            .UseSqlServer(
                "Server=nao-conecta;Database=bdautolab_ensaios;User ID=u;Password=p",
                sqlServer => sqlServer.UseCompatibilityLevel(AutoLabDbContext.SqlServerCompatibilityLevel))
            .Options);

        List<string> authorized = [.. Enumerable.Range(1, 5000).Select(index => $"{index:00000}/2026-001")];

        string sql = AutoLabQueries.EquipmentKeys(database, authorized, null)
            .OnlyThePage(new PageRequest(1, 50))
            .ToQueryString();

        Assert.DoesNotContain("OPENJSON", sql, StringComparison.Ordinal);
        Assert.True(Regex.Count(sql, @"@\w+") < 2100, sql[..400]);
        Assert.Contains("N'05000/2026-001'", sql, StringComparison.Ordinal);
    }

    private static EquipmentSearch SearchFor(string text)
    {
        Assert.True(EquipmentSearch.TryCreate(text, out EquipmentSearch? search));

        return search!;
    }

    private static string SqlOf(string name)
    {
        using AutoLabDbContext database = new(new DbContextOptionsBuilder<AutoLabDbContext>()
            .UseSqlServer("Server=nao-conecta;Database=bdautolab_ensaios;User ID=u;Password=p")
            .Options);

        PageRequest page = new(2, 50);
        List<string> values = ["571289", "111.594/1"];
        List<string> authorized = ["01417/2026-001", "01351/2026-004"];
        List<Guid?> clients = [Guid.Empty];

        return name switch
        {
            "ClientsOfCnpjs" => AutoLabQueries.ClientsOfCnpjs(database, ["30960572000183"], 5001).ToQueryString(),
            "ProtocolsOfClients" => AutoLabQueries.ProtocolsOfClients(database, clients, 5001).ToQueryString(),
            "ReportsOfClients" => AutoLabQueries.ReportsOfClients(database, clients, 5001).ToQueryString(),
            "SignedReportsOfClients" => AutoLabQueries.SignedReportsOfClients(
                database, clients, new DateTime(2026, 7, 1), new DateTime(2026, 7, 2), Guid.Empty, 100).ToQueryString(),
            "ProtocolSummaries" => AutoLabQueries.ProtocolSummaries(database, authorized, 5001).ToQueryString(),
            "ReportsOfProtocols" => AutoLabQueries.ReportsOfProtocols(database, authorized, 5001).ToQueryString(),
            "QuoteCodesOfClients" => AutoLabQueries.QuoteCodesOfClients(database, clients, 5001).ToQueryString(),
            "EquipmentKeys" => AutoLabQueries.EquipmentKeys(database, authorized, null).OnlyThePage(page).ToQueryString(),
            "EquipmentKeysOfLongestSearch" => AutoLabQueries.EquipmentKeys(database, authorized, SearchFor(new string('_', EquipmentSearch.MaxLength))).OnlyThePage(page).ToQueryString(),
            "EquipmentKeysOfSearch" => AutoLabQueries.EquipmentKeys(database, authorized, SearchFor("TR[_%\\")).OnlyThePage(page).ToQueryString(),
            "RowsOfEquipmentKeys" => AutoLabQueries.RowsOfEquipmentKeys(database, authorized, values, 5001).ToQueryString(),
            "ProtocolsOfEquipment" => AutoLabQueries.ProtocolsOfEquipment(database, authorized, "571289").OnlyThePage(page).ToQueryString(),
            "RowsOfProtocols" => AutoLabQueries.RowsOfProtocols(database, authorized, values, 5001).ToQueryString(),
            "RowsOfProtocol" => AutoLabQueries.RowsOfProtocol(database, authorized, "01417/2026-001", 5001).ToQueryString(),
            "RegistryRowsOf" => AutoLabQueries.RegistryRowsOf(database, values, 5001).ToQueryString(),
            "ProgressRowsOf" => AutoLabQueries.ProgressRowsOf(database, values, 5001).ToQueryString(),
            "ReportViewRowOf" => AutoLabQueries.ReportViewRowOf(database, Guid.Empty).ToQueryString(),
            "LiveFilesAmong" => AutoLabQueries.LiveFilesAmong(database, [Guid.Empty], 501).ToQueryString(),
            "LiveFileOf" => AutoLabQueries.LiveFileOf(database, Guid.Empty).ToQueryString(),
            _ => throw new ArgumentOutOfRangeException(nameof(name))
        };
    }
}
