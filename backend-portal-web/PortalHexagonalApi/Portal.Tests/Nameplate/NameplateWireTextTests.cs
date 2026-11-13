using Portal.Api.Nameplate;

using Xunit;

namespace Portal.Tests.Nameplate;

/// <summary>
/// A unica composicao da borda: a potencia, que e duas colunas e uma frase. Os outros oito
/// campos atravessam como o cadastro escreveu e nao tem teste, porque mapeamento trivial de
/// um campo para outro nao merece um.
/// </summary>
public sealed class NameplateWireTextTests
{
    [Fact]
    public void APowerWithAUnitTravelsAsValueAndUnit()
    {
        Assert.Equal("50 MVA", NameplateWireText.PowerOf(
            NameplateBuilder.Valid(power: "50", powerUnit: "MVA")));
    }

    /// <summary>
    /// A classe das 100 linhas com potencia e sem unidade. Completar com "kVA", a unidade
    /// mais comum, faria o portal afirmar uma grandeza que ninguem registrou, e a mesma
    /// tabela tem MVA: a diferenca entre as duas e de mil vezes.
    /// </summary>
    [Fact]
    public void APowerWithoutAUnitTravelsAsTheBareValue()
    {
        Assert.Equal("7", NameplateWireText.PowerOf(
            NameplateBuilder.Valid(power: "7", powerUnit: null)));
    }

    /// <summary>
    /// Enrolamentos multiplos atravessam inteiros. Uma borda que partisse na barra para
    /// achar um numero publicaria 2000 e esconderia 2500.
    /// </summary>
    [Fact]
    public void MultipleWindingsTravelWhole()
    {
        Assert.Equal("2000/2500 kVA", NameplateWireText.PowerOf(
            NameplateBuilder.Valid(power: "2000/2500", powerUnit: "kVA")));
    }

    [Fact]
    public void ANameplateWithoutPowerTravelsAsAbsence()
    {
        Assert.Null(NameplateWireText.PowerOf(NameplateBuilder.Valid(power: null)));
    }

    /// <summary>
    /// Equipamento sem placa nenhuma. A borda e chamada com nulo na listagem inteira quando o
    /// cadastro esta desligado, e um metodo que estourasse ali derrubaria a tela de
    /// equipamentos no deployment que roda hoje.
    /// </summary>
    [Fact]
    public void NoNameplateAtAllTravelsAsAbsence()
    {
        Assert.Null(NameplateWireText.PowerOf(null));
    }
}
