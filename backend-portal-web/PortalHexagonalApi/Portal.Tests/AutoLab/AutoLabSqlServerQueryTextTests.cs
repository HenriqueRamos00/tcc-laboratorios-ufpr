using System.Text.RegularExpressions;

using Microsoft.EntityFrameworkCore;

using Portal.Adapters.AutoLab;
using Portal.Api.AutoLab;
using Portal.Application.LabResults;
using Portal.Application.Paging;
using Portal.Application.Visibility;

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
        "EquipmentKeys",
        "EquipmentKeysOfSearch",
        "RowsOfEquipmentKeys",
        "ProtocolsOfEquipment",
        "RowsOfProtocols",
        "RowsOfProtocol",
        "RegistryRowsOf",
        "ProgressRowsOf",
        "SignedDocumentOf",
        "IdentificationsWithSignedDocument",
        "CurrentIssueOf",
        "Reports",
        "SignedDocumentOfReport"
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
            "COLLATE " + AutoLabQueries.OrdinalCollation,
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
    [InlineData("Reports")]
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
        Assert.Contains("[EmpresaTrafo]", sql, StringComparison.Ordinal);
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

            Assert.Equal(name is "SignedDocumentOf" or "SignedDocumentOfReport", readsContent);
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
        VisibilityScope company = VisibilityScope.OfCompany("EMPRESA");

        return name switch
        {
            "EquipmentKeys" => AutoLabQueries.EquipmentKeys(database, company, null).OnlyThePage(page).ToQueryString(),
            "EquipmentKeysOfLongestSearch" => AutoLabQueries.EquipmentKeys(database, company, SearchFor(new string('_', EquipmentSearch.MaxLength))).OnlyThePage(page).ToQueryString(),
            "EquipmentKeysOfSearch" => AutoLabQueries.EquipmentKeys(database, company, SearchFor("TR[_%\\")).OnlyThePage(page).ToQueryString(),
            "RowsOfEquipmentKeys" => AutoLabQueries.RowsOfEquipmentKeys(database, company, values, 5001).ToQueryString(),
            "ProtocolsOfEquipment" => AutoLabQueries.ProtocolsOfEquipment(database, company, "571289").OnlyThePage(page).ToQueryString(),
            "RowsOfProtocols" => AutoLabQueries.RowsOfProtocols(database, company, values, 5001).ToQueryString(),
            "RowsOfProtocol" => AutoLabQueries.RowsOfProtocol(database, company, "01417/2026-001", 5001).ToQueryString(),
            "RegistryRowsOf" => AutoLabQueries.RegistryRowsOf(database, values, 5001).ToQueryString(),
            "ProgressRowsOf" => AutoLabQueries.ProgressRowsOf(database, values, 5001).ToQueryString(),
            "SignedDocumentOf" => AutoLabQueries.SignedDocumentOf(database, "LACTEC-02272/2026").ToQueryString(),
            "IdentificationsWithSignedDocument" => AutoLabQueries.IdentificationsWithSignedDocument(database, values, 501).ToQueryString(),
            "CurrentIssueOf" => AutoLabQueries.CurrentIssueOf(database, "LACTEC-02272/2026").ToQueryString(),
            "Reports" => AutoLabQueries.Reports(database, VisibilityScope.All, "2026_45499").OnlyThePage(page).ToQueryString(),
            "SignedDocumentOfReport" => AutoLabQueries.SignedDocumentOfReport(database, company, Guid.Empty).ToQueryString(),
            _ => throw new ArgumentOutOfRangeException(nameof(name))
        };
    }
}
