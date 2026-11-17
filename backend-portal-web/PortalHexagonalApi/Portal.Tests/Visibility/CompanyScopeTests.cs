using Portal.Application.Visibility;

using Xunit;

namespace Portal.Tests.Visibility;

public sealed class CompanyScopeTests
{
    [Fact]
    public void TheScopeKeepsTheCnpjsOfTheSessionWithoutDuplicates()
    {
        CompanyScope scope = CompanyScope.OfCnpjs(["30960572000183", "11222333000181", "30960572000183"]);

        Assert.False(scope.IsEmpty);
        Assert.Equal(["11222333000181", "30960572000183"], scope.Cnpjs);
    }

    [Fact]
    public void NoCnpjMeansTheEmptyScopeAndNeverEveryCompany()
    {
        Assert.True(CompanyScope.OfCnpjs(null).IsEmpty);
        Assert.True(CompanyScope.OfCnpjs([]).IsEmpty);
        Assert.Same(CompanyScope.None, CompanyScope.OfCnpjs([]));
        Assert.False(CompanyScope.None.Includes("30.960.572/0001-83"));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData(" ")]
    [InlineData("*")]
    [InlineData("all")]
    [InlineData("%")]
    [InlineData("30.960.572/0001-83")]
    [InlineData("3096057200018")]
    [InlineData("309605720001833")]
    [InlineData("30960572000183 ")]
    [InlineData("30960572000183\n")]
    [InlineData("30960572000183,11222333000181")]
    [InlineData("30960572000183' OR '1'='1")]
    [InlineData("30960572ooo183")]
    [InlineData("３０９６０５７２０００１８３")]
    public void OneMalformedValueClosesTheWholeScope(string? malformed)
    {
        CompanyScope scope = CompanyScope.OfCnpjs(["30960572000183", malformed]);

        Assert.True(scope.IsEmpty);
        Assert.Empty(scope.Cnpjs);
    }

    [Theory]
    [InlineData("30.960.572/0001-83")]
    [InlineData("30960572000183")]
    [InlineData(" 30.960.572/0001-83 ")]
    [InlineData("30 960 572 0001 83")]
    public void TheStoredCnpjIsComparedWithoutItsMask(string stored)
    {
        CompanyScope scope = CompanyScope.OfCnpjs(["30960572000183"]);

        Assert.Equal("30960572000183", CompanyScope.Normalize(stored));
        Assert.True(scope.Includes(stored));
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("123.456.789-09")]
    [InlineData("30.960.572/0001-8")]
    [InlineData("30.960.572/0001-833")]
    [InlineData("30.960.572/0001-83 (antigo)")]
    [InlineData("30_960_572_0001_83")]
    [InlineData("11.222.333/0001-81")]
    public void WhatIsNotTheCnpjOfTheScopeIsNotIncluded(string? stored)
    {
        CompanyScope scope = CompanyScope.OfCnpjs(["30960572000183"]);

        Assert.False(scope.Includes(stored));
    }

    [Fact]
    public void TwoScopesOfTheSameCompaniesAreTheSameScope()
    {
        CompanyScope one = CompanyScope.OfCnpjs(["30960572000183", "11222333000181"]);
        CompanyScope other = CompanyScope.OfCnpjs(["11222333000181", "30960572000183"]);

        Assert.Equal(one, other);
        Assert.Equal(one.GetHashCode(), other.GetHashCode());
        Assert.NotEqual(one, CompanyScope.OfCnpjs(["30960572000183"]));
    }

    [Fact]
    public void TheTextOfTheScopeDoesNotCarryTheCnpjs()
    {
        string text = CompanyScope.OfCnpjs(["30960572000183"]).ToString();

        Assert.DoesNotContain("30960572000183", text, StringComparison.Ordinal);
        Assert.Contains("1", text, StringComparison.Ordinal);
    }
}
