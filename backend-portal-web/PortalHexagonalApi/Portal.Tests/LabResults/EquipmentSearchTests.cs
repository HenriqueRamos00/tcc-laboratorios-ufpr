using Portal.Adapters.AutoLab;
using Portal.Application.LabResults;

using Xunit;

namespace Portal.Tests.LabResults;

public sealed class EquipmentSearchTests
{
    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("\t\n")]
    public void TextoAusenteOuEmBrancoEListagemSemFiltro(string? text)
    {
        Assert.True(EquipmentSearch.TryCreate(text, out EquipmentSearch? search));
        Assert.Null(search);
    }

    [Theory]
    [InlineData("571289", "571289")]
    [InlineData("  TR-00001  ", "TR-00001")]
    [InlineData("100%", "100%")]
    [InlineData("TR_1 [A]", "TR_1 [A]")]
    public void OTermoEAparadoEOsCuringasFicamComoTextoLiteral(string text, string expected)
    {
        Assert.True(EquipmentSearch.TryCreate(text, out EquipmentSearch? search));
        Assert.Equal(expected, search?.Term);
    }

    [Fact]
    public void OTamanhoMaximoEContadoDepoisDeAparar()
    {
        string atTheLimit = new('7', EquipmentSearch.MaxLength);

        Assert.True(EquipmentSearch.TryCreate(" " + atTheLimit + " ", out EquipmentSearch? accepted));
        Assert.Equal(atTheLimit, accepted?.Term);

        Assert.False(EquipmentSearch.TryCreate(atTheLimit + "7", out EquipmentSearch? refused));
        Assert.Null(refused);
    }

    [Theory]
    [InlineData("571289", "%571289%")]
    [InlineData("100%", @"%100\%%")]
    [InlineData("TR_1", @"%TR\_1%")]
    [InlineData("[A-Z]", @"%\[A-Z]%")]
    [InlineData(@"A\B", @"%A\\B%")]
    [InlineData(@"\%", @"%\\\%%")]
    public void OPadraoDeLikeNeutralizaOsCuringasEOProprioEscape(string text, string expected)
    {
        Assert.Equal(expected, LikePattern.Containing(text));
    }
}
