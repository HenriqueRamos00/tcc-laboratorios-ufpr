using Microsoft.EntityFrameworkCore;

using Portal.Adapters.AutoLab;
using Portal.Api.AutoLab;
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
        Assert.Equal(15, built.CommandTimeout);
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
            "EquipmentKeys" => AutoLabQueries.EquipmentKeys(database, company).OnlyThePage(page).ToQueryString(),
            "RowsOfEquipmentKeys" => AutoLabQueries.RowsOfEquipmentKeys(database, company, values, 5001).ToQueryString(),
            "ProtocolsOfEquipment" => AutoLabQueries.ProtocolsOfEquipment(database, company, "571289").OnlyThePage(page).ToQueryString(),
            "RowsOfProtocols" => AutoLabQueries.RowsOfProtocols(database, company, values, 5001).ToQueryString(),
            "RowsOfProtocol" => AutoLabQueries.RowsOfProtocol(database, company, "01417/2026-001", 5001).ToQueryString(),
            "RegistryRowsOf" => AutoLabQueries.RegistryRowsOf(database, values, 5001).ToQueryString(),
            "ProgressRowsOf" => AutoLabQueries.ProgressRowsOf(database, values, 5001).ToQueryString(),
            "SignedDocumentOf" => AutoLabQueries.SignedDocumentOf(database, "LACTEC-02272/2026").ToQueryString(),
            "IdentificationsWithSignedDocument" => AutoLabQueries.IdentificationsWithSignedDocument(database, values, 501).ToQueryString(),
            "CurrentIssueOf" => AutoLabQueries.CurrentIssueOf(database, "LACTEC-02272/2026").ToQueryString(),
            "Reports" => AutoLabQueries.Reports(database, VisibilityScope.All, "EAQ_2026_45499_V_1").OnlyThePage(page).ToQueryString(),
            "SignedDocumentOfReport" => AutoLabQueries.SignedDocumentOfReport(database, company, Guid.Empty).ToQueryString(),
            _ => throw new ArgumentOutOfRangeException(nameof(name))
        };
    }
}
