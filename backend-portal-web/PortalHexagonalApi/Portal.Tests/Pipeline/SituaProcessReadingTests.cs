using Portal.Adapters.Pipeline;
using Portal.Domain.Pipeline;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// A tabela de codigos do AutoLAB e o casamento por OrcamentoCRM. Os valores sao os do
/// censo das 3345 linhas, e os limites de cada coluna estao exercitados dos dois lados,
/// inclusive fora do que a amostra real tem.
/// </summary>
public sealed class SituaProcessReadingTests
{
    [Theory]
    [InlineData(null, PipelineStage.InProgress)]
    [InlineData(0, PipelineStage.InProgress)]
    [InlineData(1, PipelineStage.Complete)]
    [InlineData(2, PipelineStage.Validated)]
    [InlineData(3, PipelineStage.Finalized)]
    public void EachValueOfSituacaoEnsaiosNamesItsStage(int? situacaoEnsaios, PipelineStage expected)
    {
        Assert.Equal<PipelineStage?>(expected, SituaProcessReading.StageOfOneRow(
            RowOf(situacaoEnsaios: situacaoEnsaios)));
    }

    /// <summary>
    /// 3, 4 e 5 existem na amostra real e caem todos no mesmo degrau, sem variacao. O 6 nao
    /// existe e esta aqui porque "igual a 3" e "3 ou mais" sao duas implementacoes
    /// diferentes que concordam em 3 e discordam em 4.
    /// </summary>
    [Theory]
    [InlineData(3)]
    [InlineData(4)]
    [InlineData(5)]
    [InlineData(6)]
    public void SituacaoEnsaiosOfThreeOrMoreStaysAtFinalized(int situacaoEnsaios)
    {
        Assert.Equal<PipelineStage?>(
            PipelineStage.Finalized,
            SituaProcessReading.StageOfOneRow(RowOf(situacaoEnsaios: situacaoEnsaios)));
    }

    /// <summary>
    /// Abaixo de zero a fonte nunca chega, e uma tabela escrita so com os valores vistos
    /// manda o negativo para o degrau mais alto pelo caminho do caso que sobra.
    /// </summary>
    [Fact]
    public void ANegativeSituacaoEnsaiosIsReadAsZeroAndNotAsFinalized()
    {
        Assert.Equal<PipelineStage?>(
            PipelineStage.InProgress,
            SituaProcessReading.StageOfOneRow(RowOf(situacaoEnsaios: -1)));
    }

    [Theory]
    [InlineData(1, PipelineStage.DraftingReport)]
    [InlineData(2, PipelineStage.ReportPublished)]
    public void SolicitacaoRelatorioOneAndTwoNameTheReportStages(
        int solicitacaoRelatorio,
        PipelineStage expected)
    {
        Assert.Equal<PipelineStage?>(expected, SituaProcessReading.StageOfOneRow(
            RowOf(situacaoEnsaios: 0, solicitacaoRelatorio: solicitacaoRelatorio)));
    }

    [Theory]
    [InlineData(null)]
    [InlineData(-1)]
    [InlineData(0)]
    [InlineData(3)]
    public void SolicitacaoRelatorioOutsideOneAndTwoDoesNotAddAStage(int? solicitacaoRelatorio)
    {
        Assert.Equal<PipelineStage?>(
            PipelineStage.Finalized,
            SituaProcessReading.StageOfOneRow(
                RowOf(situacaoEnsaios: 3, solicitacaoRelatorio: solicitacaoRelatorio)));
    }

    /// <summary>
    /// As 69 linhas medidas com 1|1|2|1. O relatorio comecou antes de o ensaio sair de
    /// validado, e a etapa sobe sem que SituacaoEnsaios mude: decidir a etapa por uma coluna
    /// so erra nessas 69.
    /// </summary>
    [Fact]
    public void AReportBeingDraftedOvertakesAValidatedTest()
    {
        Assert.Equal<PipelineStage?>(
            PipelineStage.DraftingReport,
            SituaProcessReading.StageOfOneRow(RowOf(situacaoEnsaios: 2, solicitacaoRelatorio: 1)));
    }

    [Fact]
    public void ARowThatDidNotArriveHasNoStageWhateverTheOtherColumnsSay()
    {
        Assert.Null(SituaProcessReading.StageOfOneRow(new SituaProcessRow
        {
            OrcamentoCRM = "2026_49003",
            Recebido = 0,
            Protocolado = 0,
            SituacaoEnsaios = 1,
            SolicitacaoRelatorio = 0
        }));
    }

    /// <summary>
    /// Combinacao ausente das 3345: Recebido e Protocolado nunca divergem. Exigir os dois e
    /// identico no dado medido e mais conservador no dado que ninguem viu, e e este teste
    /// que avisa se alguem resolver afrouxar a porteira.
    /// </summary>
    [Fact]
    public void ASampleReceivedWithoutAProtocolHasNoStage()
    {
        Assert.Null(SituaProcessReading.StageOfOneRow(new SituaProcessRow
        {
            OrcamentoCRM = "2026_49004",
            Recebido = 1,
            Protocolado = 0,
            SituacaoEnsaios = 3,
            SolicitacaoRelatorio = 2
        }));
    }

    [Fact]
    public void ARowWithNullInTheEntryColumnsHasNoStage()
    {
        Assert.Null(SituaProcessReading.StageOfOneRow(new SituaProcessRow
        {
            OrcamentoCRM = "2026_49005",
            Recebido = null,
            Protocolado = null,
            SituacaoEnsaios = 3,
            SolicitacaoRelatorio = 2
        }));
    }

    /// <summary>
    /// Os dois degraus que esta fonte nunca produz, varridos sobre o produto das colunas.
    /// O briefing pede que isso seja dito em voz alta, e dizer em teste e o jeito de a
    /// afirmacao envelhecer junto com o codigo: se a regra do nulo mudar, este teste cai.
    /// </summary>
    [Fact]
    public void ThisSourceNeverProducesReceivedNorProtocolAssigned()
    {
        int?[] recebido = [null, 0, 1];
        int?[] protocolado = [null, 0, 1];
        int?[] situacaoEnsaios = [null, 0, 1, 2, 3, 4, 5];
        int?[] solicitacaoRelatorio = [null, 0, 1, 2];

        IEnumerable<PipelineStage?> produced =
            from entered in recebido
            from protocol in protocolado
            from testing in situacaoEnsaios
            from report in solicitacaoRelatorio
            select SituaProcessReading.StageOfOneRow(new SituaProcessRow
            {
                OrcamentoCRM = "2026_49006",
                Recebido = entered,
                Protocolado = protocol,
                SituacaoEnsaios = testing,
                SolicitacaoRelatorio = report
            });

        Assert.DoesNotContain(PipelineStage.Received, produced);
        Assert.DoesNotContain(PipelineStage.ProtocolAssigned, produced);
    }

    [Fact]
    public void RowsOfTheSameOrcamentoCRMBecomeOneProgressAtTheLeastAdvancedStage()
    {
        SituaProcessRow[] rows =
        [
            RowOf(code: "2026_49010", situacaoEnsaios: 3, solicitacaoRelatorio: 2),
            RowOf(code: "2026_49010", situacaoEnsaios: 0, solicitacaoRelatorio: 0)
        ];

        LaboratoryProgress progress = Assert.Single(
            SituaProcessReading.ProgressOf(rows, ["2026_49010"]));

        Assert.Equal("2026_49010", progress.QuoteCode);
        Assert.Equal(PipelineStage.InProgress, progress.Stage);
    }

    [Fact]
    public void AQuoteWithOneRowOutsideTheLaboratoryProducesNoProgressAtAll()
    {
        SituaProcessRow[] rows =
        [
            RowOf(code: "2026_49011", situacaoEnsaios: 3, solicitacaoRelatorio: 2),
            new SituaProcessRow
            {
                OrcamentoCRM = "2026_49011",
                Recebido = 0,
                Protocolado = 0,
                SituacaoEnsaios = null,
                SolicitacaoRelatorio = 0
            }
        ];

        Assert.Empty(SituaProcessReading.ProgressOf(rows, ["2026_49011"]));
    }

    /// <summary>
    /// O sufixo livre distingue usinas diferentes e nao e sujeira. Casar por prefixo funde
    /// dois orcamentos distintos num so, e o portal passa a afirmar a etapa de uma usina na
    /// linha de outra.
    /// </summary>
    [Fact]
    public void OrcamentoCRMIsMatchedWholeAndNeverByPrefix()
    {
        SituaProcessRow[] rows =
        [
            RowOf(code: "2026_49001", situacaoEnsaios: 4, solicitacaoRelatorio: 2),
            RowOf(code: "2026_49001_UHAB Agua Boa", situacaoEnsaios: 0, solicitacaoRelatorio: 0)
        ];

        LaboratoryProgress progress = Assert.Single(
            SituaProcessReading.ProgressOf(rows, ["2026_49001"]));

        Assert.Equal("2026_49001", progress.QuoteCode);
        Assert.Equal(PipelineStage.ReportPublished, progress.Stage);
    }

    [Fact]
    public void ACodeWithASpacedPrefixIsMatchedAsItIsWritten()
    {
        SituaProcessRow[] rows = [RowOf(code: "EAQ 2026_49002", situacaoEnsaios: 3, solicitacaoRelatorio: 1)];

        LaboratoryProgress progress = Assert.Single(
            SituaProcessReading.ProgressOf(rows, ["EAQ 2026_49002"]));

        Assert.Equal(PipelineStage.DraftingReport, progress.Stage);
    }

    /// <summary>
    /// Coluna char no SQL Server chega preenchida com espaco a direita. Sem aparar as
    /// pontas, a fatia funciona em todo teste e nao promove nada em producao.
    /// </summary>
    [Fact]
    public void TrailingSpacesFromTheSourceDoNotHideTheProgress()
    {
        SituaProcessRow[] rows = [RowOf(code: "2026_49012   ", situacaoEnsaios: 3, solicitacaoRelatorio: 2)];

        LaboratoryProgress progress = Assert.Single(
            SituaProcessReading.ProgressOf(rows, ["2026_49012"]));

        Assert.Equal("2026_49012", progress.QuoteCode);
    }

    [Fact]
    public void OnlyTheQuotesAskedAboutAreAnswered()
    {
        SituaProcessRow[] rows =
        [
            RowOf(code: "2026_49013", situacaoEnsaios: 3, solicitacaoRelatorio: 2),
            RowOf(code: "2026_49014", situacaoEnsaios: 0, solicitacaoRelatorio: 0)
        ];

        LaboratoryProgress progress = Assert.Single(
            SituaProcessReading.ProgressOf(rows, ["2026_49013"]));

        Assert.Equal("2026_49013", progress.QuoteCode);
    }

    [Fact]
    public void AQuoteCodeThatNoRowCarriesHasNoProgress()
    {
        SituaProcessRow[] rows = [RowOf(code: "2026_49015", situacaoEnsaios: 3, solicitacaoRelatorio: 2)];

        Assert.Empty(SituaProcessReading.ProgressOf(rows, ["2026_49999"]));
    }

    [Fact]
    public void AskingWithoutAnyCodeAnswersNothing()
    {
        SituaProcessRow[] rows = [RowOf(code: "2026_49016", situacaoEnsaios: 3, solicitacaoRelatorio: 2)];

        Assert.Empty(SituaProcessReading.ProgressOf(rows, []));
    }

    /// <summary>
    /// O adaptador do Salesforce mapeia codigo ausente para texto vazio. Sem descartar o
    /// vazio dos dois lados, um orcamento sem codigo casaria com uma linha sem OrcamentoCRM
    /// e receberia a etapa de ninguem.
    /// </summary>
    [Fact]
    public void ABlankCodeNeverMatchesABlankRow()
    {
        SituaProcessRow[] rows = [RowOf(code: "", situacaoEnsaios: 3, solicitacaoRelatorio: 2)];

        Assert.Empty(SituaProcessReading.ProgressOf(rows, [""]));
        Assert.Empty(SituaProcessReading.ProgressOf(rows, ["   "]));
    }

    private static SituaProcessRow RowOf(
        string code = "2026_49000",
        int? situacaoEnsaios = 0,
        int? solicitacaoRelatorio = 0)
    {
        return new SituaProcessRow
        {
            OrcamentoCRM = code,
            Recebido = 1,
            Protocolado = 1,
            SituacaoEnsaios = situacaoEnsaios,
            SolicitacaoRelatorio = solicitacaoRelatorio
        };
    }
}
