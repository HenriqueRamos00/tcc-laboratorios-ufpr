using Portal.Domain.Nameplate;
using Portal.Domain.Nameplate.Exceptions;

using Xunit;

namespace Portal.Tests.Nameplate;

/// <summary>
/// A regra de concordancia campo a campo, que e o nucleo desta fatia. Nenhum caso aqui
/// precisa de fonte: as entradas sao placas montadas a mao, e e por isso que a regra pode ser
/// exercitada nos limites que o cadastro real nao oferece.
///
/// As classes de equivalencia da regra sao quatro por campo: ninguem afirma, um afirma, varios
/// afirmam a mesma coisa, varios afirmam coisas diferentes. Os limites da colecao sao zero
/// linhas, uma, duas e tres.
/// </summary>
public sealed class NameplateAgreementTests
{
    /// <summary>
    /// Valor-limite da colecao: zero linhas. O cadastro nao conhece a serie, e a resposta nao
    /// e uma placa vazia.
    /// </summary>
    [Fact]
    public void ASerialNumberWithNoRowInTheRegistryHasNoNameplate()
    {
        Assert.Null(NameplateAgreement.Of("9000001", []));
    }

    /// <summary>
    /// Valor-limite da colecao: uma linha. Nao ha com quem discordar, e todo campo escrito
    /// sobrevive.
    /// </summary>
    [Fact]
    public void ASingleRowStatesEveryFieldItWrote()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of(
            "9000001", [NameplateBuilder.Valid()]);

        Assert.NotNull(nameplate);
        Assert.Equal("TR-00011", nameplate.Tag);
        Assert.Equal("2014", nameplate.ManufacturingYear);
        Assert.Equal("111", nameplate.Power?.Value);
        Assert.Equal("MVA", nameplate.Power?.Unit);
        Assert.Equal("525", nameplate.Voltage);
        Assert.Equal("REATOR", nameplate.EquipmentType);
    }

    [Fact]
    public void TwoRowsThatWriteTheSameThingStateIt()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of(
            "9000001", [NameplateBuilder.Valid(), NameplateBuilder.Valid()]);

        Assert.NotNull(nameplate);
        Assert.Equal("525", nameplate.Voltage);
    }

    /// <summary>
    /// O caso medido de 1.110.009/4, que aparece com tensao 525 e 13,8 na mesma TAG. Nao ha
    /// <c>id</c> nem data na tabela, entao nao existe linha mais recente: a tensao cai e tudo
    /// em que as duas linhas concordam fica de pe.
    /// </summary>
    [Fact]
    public void AFieldTheRowsContradictIsNotStatedAndTheRestSurvives()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of("9000001", [
            NameplateBuilder.Valid(voltage: "525"),
            NameplateBuilder.Valid(voltage: "13,8")]);

        Assert.NotNull(nameplate);
        Assert.Null(nameplate.Voltage);
        Assert.Equal("FABRICANTE DE REATORES", nameplate.Manufacturer);
        Assert.Equal("Óleo Mineral Isolante", nameplate.OilType);
        Assert.Equal("111", nameplate.Power?.Value);
    }

    /// <summary>
    /// O caso medido de 257373, que tem uma linha com tres colunas em branco. Branco quer
    /// dizer "o cadastro nao diz", e nao "o cadastro diz outra coisa": tratar branco como
    /// divergencia jogaria fora tres campos bons por nada.
    /// </summary>
    [Fact]
    public void ABlankFieldAbstainsInsteadOfContradicting()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of("9000002", [
            NameplateBuilder.Valid(serialNumber: "9000002", oilType: null, equipmentType: null),
            NameplateBuilder.Valid(serialNumber: "9000002")]);

        Assert.NotNull(nameplate);
        Assert.Equal("Óleo Mineral Isolante", nameplate.OilType);
        Assert.Equal("REATOR", nameplate.EquipmentType);
    }

    /// <summary>
    /// A forma medida de 111.659/10: tres linhas, e a terceira quase vazia ainda assim afirma
    /// um terceiro tipo. Valor-limite da colecao em tres, porque "duas discordam" e "tres
    /// discordam" sao caminhos diferentes de uma implementacao que comparasse par a par.
    /// </summary>
    [Fact]
    public void ThreeDifferentStatementsOfOneFieldDropOnlyThatField()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of("9000007", [
            NameplateBuilder.Valid(serialNumber: "9000007", equipmentType: "REATOR"),
            NameplateBuilder.Valid(serialNumber: "9000007", equipmentType: "REATOR MONOFÁSICO"),
            NameplateBuilder.SayingOnly("9000007", "REATOR DE DERIVAÇÃO")]);

        Assert.NotNull(nameplate);
        Assert.Null(nameplate.EquipmentType);
        Assert.Equal("525", nameplate.Voltage);
        Assert.Equal("TR-00011", nameplate.Tag);
    }

    /// <summary>
    /// A classe das 12 linhas que trazem a serie e mais nada. Uma placa que nao afirma campo
    /// nenhum nao e placa: devolver o objeto vazio faria a tela anunciar "cadastro
    /// encontrado" e em seguida imprimir "Nao informado" sete vezes.
    /// </summary>
    [Fact]
    public void ARowThatOnlyCarriesTheSerialNumberIsNotANameplate()
    {
        Assert.Null(NameplateAgreement.Of(
            "9000005", [NameplateBuilder.SayingOnly("9000005", null)]));
    }

    /// <summary>
    /// Divergencia so na unidade. Quanto o equipamento tem continua sendo um fato, e e a
    /// unidade que ficou em disputa: derrubar a potencia inteira perderia informacao que as
    /// duas linhas afirmam igual.
    /// </summary>
    [Fact]
    public void APowerWhoseUnitIsContradictedKeepsTheValueAndLosesTheUnit()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of("9000001", [
            NameplateBuilder.Valid(power: "500", powerUnit: "kVA"),
            NameplateBuilder.Valid(power: "500", powerUnit: "MVA")]);

        Assert.NotNull(nameplate);
        Assert.Equal("500", nameplate.Power?.Value);
        Assert.Null(nameplate.Power?.Unit);
    }

    /// <summary>
    /// Divergencia no valor derruba a unidade com ele. Guardar a unidade sozinha publicaria
    /// "kVA" na coluna de potencia, o que afirma que o cadastro informa a potencia.
    /// </summary>
    [Fact]
    public void APowerWhoseValueIsContradictedTakesItsUnitDownWithIt()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of("9000001", [
            NameplateBuilder.Valid(power: "37,500", powerUnit: "kVA"),
            NameplateBuilder.Valid(power: "35.500", powerUnit: "kVA")]);

        Assert.NotNull(nameplate);
        Assert.Null(nameplate.Power);
    }

    /// <summary>
    /// A comparacao e textual e nao normaliza numero, e o preco esta declarado: 111 e 111,00
    /// sao a mesma potencia escrita de dois jeitos e a placa fica sem potencia. Normalizar
    /// exigiria interpretar a coluna como decimal, que as formas com barra proibem, e nessa
    /// mesma tabela 37,500 e 35.500 sao numeros diferentes, nao grafias do mesmo.
    /// </summary>
    [Fact]
    public void TheSameNumberWrittenTwoWaysIsTreatedAsDisagreement()
    {
        EquipmentNameplate? nameplate = NameplateAgreement.Of("9000001", [
            NameplateBuilder.Valid(power: "111"),
            NameplateBuilder.Valid(power: "111,00")]);

        Assert.NotNull(nameplate);
        Assert.Null(nameplate.Power);
        Assert.Equal("525", nameplate.Voltage);
    }

    /// <summary>
    /// Agrupar errado antes de chamar produziria uma placa inventada, com o fabricante de um
    /// equipamento e a tensao de outro, e nenhuma resposta HTTP ficaria invalida por isso. O
    /// numero de serie recusado nao aparece na mensagem, porque e dado do cliente.
    /// </summary>
    [Fact]
    public void ARowOfAnotherSerialNumberIsRefusedWithoutNamingTheValue()
    {
        NameplateOfAnotherSerialNumberException failure =
            Assert.Throws<NameplateOfAnotherSerialNumberException>(() => NameplateAgreement.Of(
                "9000001",
                [NameplateBuilder.Valid(serialNumber: "9000002")]));

        Assert.DoesNotContain("9000002", failure.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void ANameplateWithoutASerialNumberCannotBeBuilt()
    {
        Assert.Throws<NameplateWithoutSerialNumberException>(
            () => NameplateBuilder.Valid(serialNumber: "   "));
    }

    /// <summary>
    /// A unidade orfa e a classe das 7 linhas com <c>UnidadePotencia</c> e sem
    /// <c>Potencia</c>. O tipo recusa por construcao, para nenhum adaptador novo promover a
    /// unidade a grandeza.
    /// </summary>
    [Fact]
    public void APowerUnitWithoutAValueCannotBeBuilt()
    {
        Assert.Throws<PowerUnitWithoutValueException>(() => new PowerRating("", "kVA"));
    }
}
