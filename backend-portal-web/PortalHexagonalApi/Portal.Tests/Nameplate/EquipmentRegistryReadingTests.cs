using Portal.Adapters.Nameplate;
using Portal.Domain.Nameplate;

using Xunit;

namespace Portal.Tests.Nameplate;

/// <summary>
/// A leitura das dez colunas do cadastro e o casamento pela serie. Os valores sao as formas do
/// censo das 20575 linhas, e cada coluna esta exercitada nas tres classes que ela tem: valor
/// escrito, vazio e marcador.
/// </summary>
public sealed class EquipmentRegistryReadingTests
{
    [Fact]
    public void EveryColumnWrittenInTheRowBecomesAStatedField()
    {
        EquipmentNameplate nameplate = EquipmentRegistryReading.NameplateWrittenIn(new EquipmentRow
        {
            NumSerie = "9000001", Tag = "TR-00011", AnoFabricacao = "2014",
            Potencia = "111", UnidadePotencia = "MVA", Tensao = "525",
            Fabricante = "FABRICANTE DE REATORES", VolumeOleo = "23600",
            TipoDeOleo = "Óleo Mineral Isolante", TipoEquipamento = "REATOR"
        });

        Assert.Equal("9000001", nameplate.SerialNumber);
        Assert.Equal("TR-00011", nameplate.Tag);
        Assert.Equal("2014", nameplate.ManufacturingYear);
        Assert.Equal("111", nameplate.Power?.Value);
        Assert.Equal("MVA", nameplate.Power?.Unit);
        Assert.Equal("525", nameplate.Voltage);
        Assert.Equal("FABRICANTE DE REATORES", nameplate.Manufacturer);
        Assert.Equal("23600", nameplate.OilVolume);
        Assert.Equal("Óleo Mineral Isolante", nameplate.OilType);
        Assert.Equal("REATOR", nameplate.EquipmentType);
    }

    /// <summary>
    /// Particionamento por classe de escrita: os cinco marcadores medidos valem o mesmo que
    /// vazio. Publicar o marcador faria a tela imprimir "-" na coluna de potencia, que parece
    /// um valor.
    /// </summary>
    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData("-")]
    [InlineData("NA")]
    [InlineData("na")]
    [InlineData("S/N")]
    [InlineData("#")]
    [InlineData("*")]
    public void AMarkerInTheSourceMeansTheFieldIsNotStated(string written)
    {
        EquipmentNameplate nameplate = EquipmentRegistryReading.NameplateWrittenIn(new EquipmentRow
        {
            NumSerie = "9000004",
            Potencia = written,
            Tensao = written,
            AnoFabricacao = written,
            VolumeOleo = written,
            TipoEquipamento = "TAMBOR"
        });

        Assert.Null(nameplate.Power);
        Assert.Null(nameplate.Voltage);
        Assert.Null(nameplate.ManufacturingYear);
        Assert.Null(nameplate.OilVolume);
        Assert.Equal("TAMBOR", nameplate.EquipmentType);
    }

    /// <summary>
    /// A classe das 100 linhas com potencia e sem unidade. A potencia existe e nao tem
    /// unidade, e nao e nem potencia nenhuma nem potencia em kVA.
    /// </summary>
    [Fact]
    public void APowerWithoutAUnitIsStatedWithoutAUnit()
    {
        EquipmentNameplate nameplate = EquipmentRegistryReading.NameplateWrittenIn(new EquipmentRow
        {
            NumSerie = "36170301", Potencia = "7", UnidadePotencia = ""
        });

        Assert.Equal("7", nameplate.Power?.Value);
        Assert.Null(nameplate.Power?.Unit);
    }

    /// <summary>
    /// A classe das 7 linhas com unidade e sem potencia. A unidade orfa e descartada na
    /// leitura, em vez de chegar ao dominio e derrubar a linha inteira pela excecao.
    /// </summary>
    [Fact]
    public void APowerUnitWithoutAValueIsDiscardedInsteadOfRefusingTheRow()
    {
        EquipmentNameplate nameplate = EquipmentRegistryReading.NameplateWrittenIn(new EquipmentRow
        {
            NumSerie = "9000006", Potencia = "", UnidadePotencia = "KVAr", Tensao = "525"
        });

        Assert.Null(nameplate.Power);
        Assert.Equal("525", nameplate.Voltage);
    }

    /// <summary>
    /// As formas que provam que nenhuma coluna pode ser numero. Potencia com barra sao
    /// enrolamentos multiplos, tensao com barra e primario e secundario, ano em mes e ano, e
    /// volume de oleo com unidade dentro do valor.
    /// </summary>
    [Fact]
    public void TheFormsThatAreNotNumbersTravelWhole()
    {
        EquipmentNameplate nameplate = EquipmentRegistryReading.NameplateWrittenIn(new EquipmentRow
        {
            NumSerie = "9000003",
            Potencia = "2000/2500", UnidadePotencia = "kVA",
            Tensao = "13,800/220", AnoFabricacao = "05/25", VolumeOleo = "1L"
        });

        Assert.Equal("2000/2500", nameplate.Power?.Value);
        Assert.Equal("13,800/220", nameplate.Voltage);
        Assert.Equal("05/25", nameplate.ManufacturingYear);
        Assert.Equal("1L", nameplate.OilVolume);
    }

    [Fact]
    public void OnlyTheSerialNumbersAskedAboutAreAnswered()
    {
        EquipmentRow[] rows =
        [
            RowOf("9000010", "TR-00020"),
            RowOf("9000011", "TR-00021")
        ];

        EquipmentNameplate nameplate = Assert.Single(
            EquipmentRegistryReading.NameplatesOf(rows, ["9000010"]));

        Assert.Equal("9000010", nameplate.SerialNumber);
    }

    [Fact]
    public void ASerialNumberThatNoRowCarriesHasNoNameplate()
    {
        Assert.Empty(EquipmentRegistryReading.NameplatesOf([RowOf("9000010", "TR-00020")], ["884120"]));
    }

    [Fact]
    public void AskingWithoutAnySerialNumberAnswersNothing()
    {
        Assert.Empty(EquipmentRegistryReading.NameplatesOf([RowOf("9000010", "TR-00020")], []));
    }

    /// <summary>
    /// A serie vazia da requisicao nunca casa com a linha de serie vazia do cadastro. Sao 2733
    /// linhas sem serie, e casar vazio com vazio publicaria uma delas em todo equipamento
    /// enderecado pelo protocolo Lactec.
    /// </summary>
    [Fact]
    public void ABlankSerialNumberNeverMatchesABlankRow()
    {
        EquipmentRow[] rows = [RowOf("", "TR-00018"), RowOf("S/N", "TR-00019")];

        Assert.Empty(EquipmentRegistryReading.NameplatesOf(rows, [""]));
        Assert.Empty(EquipmentRegistryReading.NameplatesOf(rows, ["   "]));
        Assert.Empty(EquipmentRegistryReading.NameplatesOf(rows, ["S/N"]));
    }

    /// <summary>
    /// Coluna de texto no SQL Server chega com espaco a direita por preenchimento. Sem aparar
    /// as pontas a placa existe no banco e nunca aparece na tela.
    /// </summary>
    [Fact]
    public void TrailingSpacesFromTheSourceDoNotHideTheNameplate()
    {
        EquipmentNameplate nameplate = Assert.Single(
            EquipmentRegistryReading.NameplatesOf([RowOf("9000010   ", "TR-00020")], ["9000010"]));

        Assert.Equal("9000010", nameplate.SerialNumber);
        Assert.Equal("TR-00020", nameplate.Tag);
    }

    /// <summary>
    /// O casamento e pela serie inteira. Existe 5233 e existe 52330 no cadastro: casar por
    /// prefixo publicaria a placa de um transformador na linha de outro, possivelmente de
    /// outro cliente.
    /// </summary>
    [Fact]
    public void ASerialNumberIsMatchedWholeAndNeverByPrefix()
    {
        EquipmentRow[] rows = [RowOf("5233", "TR-00004"), RowOf("52330", "TR-00005")];

        EquipmentNameplate nameplate = Assert.Single(
            EquipmentRegistryReading.NameplatesOf(rows, ["5233"]));

        Assert.Equal("TR-00004", nameplate.Tag);
    }

    /// <summary>
    /// As linhas repetidas de uma serie viram uma placa so, e e a concordancia que decide o
    /// que ela afirma. Sem o agrupamento, duas placas da mesma serie chegariam a listagem e a
    /// primeira que casasse ganharia, que e o desempate que esta fatia recusa.
    /// </summary>
    [Fact]
    public void RowsOfTheSameSerialNumberBecomeOneNameplate()
    {
        EquipmentRow[] rows =
        [
            RowOf("9000001", "TR-00011", tensao: "525"),
            RowOf("9000001", "TR-00011", tensao: "13,8")
        ];

        EquipmentNameplate nameplate = Assert.Single(
            EquipmentRegistryReading.NameplatesOf(rows, ["9000001"]));

        Assert.Null(nameplate.Voltage);
        Assert.Equal("TR-00011", nameplate.Tag);
    }

    /// <summary>
    /// A amostra escrita a mao nao pode copiar linha real, e a conferencia fica aqui para
    /// envelhecer junto com ela: dez linhas do cadastro carregam nome de cliente dentro de
    /// <c>NumSerie</c> e de <c>Tag</c>, e o fabricante real junto com a potencia identifica o
    /// parque do cliente.
    ///
    /// A afirmacao e pela forma inventada, e nao por uma lista de nomes proibidos. Uma lista
    /// de nomes dentro deste arquivo faria a propria varredura de nome de cliente do
    /// repositorio passar a acusar um acerto, e quem a rodasse teria de decidir caso a caso
    /// se o acerto e massa de teste ou verificacao. A afirmacao pela forma
    /// pega qualquer linha copiada, inclusive de um cliente que ninguem pensou em listar.
    /// </summary>
    [Fact]
    public void TheWrittenSampleOnlyUsesInventedTagsAndManufacturers()
    {
        string[] inventedManufacturers =
        [
            "",
            "FABRICANTE DE TRANSFORMADORES",
            "FABRICANTE DE REATORES",
            "MONTADORA DE EQUIPAMENTOS"
        ];

        Assert.All(Portal.Tests.AutoLab.SampleAnalyses.RegistryRows, row =>
        {
            Assert.Matches(@"^(TR-\d{5})?$", row.Tag ?? string.Empty);
            Assert.Contains(row.Fabricante ?? string.Empty, inventedManufacturers);
        });
    }

    /// <summary>
    /// As duas series que este projeto inventou nao estao no cadastro de verdade, e nao podem
    /// estar na amostra: e esse par que prova que a juncao e pelo numero de serie real.
    /// </summary>
    [Fact]
    public void TheWrittenSampleDoesNotKnowTheInventedSerialNumbers()
    {
        Assert.Empty(EquipmentRegistryReading.NameplatesOf(
            Portal.Tests.AutoLab.SampleAnalyses.RegistryRows,
            ["884120", "884121"]));
    }

    private static EquipmentRow RowOf(string numSerie, string tag, string tensao = "525")
    {
        return new EquipmentRow
        {
            NumSerie = numSerie,
            Tag = tag,
            AnoFabricacao = "2014",
            Potencia = "111",
            UnidadePotencia = "MVA",
            Tensao = tensao,
            Fabricante = "FABRICANTE DE REATORES",
            VolumeOleo = "23600",
            TipoDeOleo = "Óleo Mineral Isolante",
            TipoEquipamento = "REATOR"
        };
    }
}
