using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

/// <summary>
/// A leitura da etapa comercial, que e a mesma tabela do <c>recognizeStage</c> do frontend.
/// Os dois lados decidem a mesma coisa sobre o mesmo orcamento, e o que esta sob teste aqui e
/// a igualdade entre eles.
/// </summary>
public sealed class CommercialStageTests
{
    [Theory]
    [InlineData("Qualificação", PipelineStage.Qualification)]
    [InlineData("Em análise pela área", PipelineStage.AreaAnalysis)]
    [InlineData("Em revisão", PipelineStage.AreaAnalysis)]
    [InlineData("Elaborando proposta", PipelineStage.DraftingProposal)]
    [InlineData("Negociação", PipelineStage.Negotiation)]
    [InlineData("Aprovado pelo cliente", PipelineStage.ApprovedByClient)]
    [InlineData("Aguardando entrega da amostra", PipelineStage.AwaitingSample)]
    [InlineData("Fechado ganho", PipelineStage.Received)]
    [InlineData("Protocolado", PipelineStage.ProtocolAssigned)]
    [InlineData("Em execução", PipelineStage.InProgress)]
    [InlineData("Completo", PipelineStage.Complete)]
    [InlineData("Validado", PipelineStage.Validated)]
    [InlineData("Finalizado", PipelineStage.Finalized)]
    [InlineData("Elaborando relatório", PipelineStage.DraftingReport)]
    [InlineData("Relatório publicado", PipelineStage.ReportPublished)]
    public void CadaEtapaDoSalesforceCaiNoDegrauQueOFrontendUsa(string written, PipelineStage expected)
    {
        Assert.Equal(expected, CommercialStage.AsTheSourceWroteIt(written).Reached);
    }

    [Theory]
    [InlineData("Fechado perdido")]
    [InlineData("Fechado recusado Lactec")]
    [InlineData("Cancelado")]
    public void EtapaDeEncerramentoNaoTemDegrau(string written)
    {
        CommercialStage stage = CommercialStage.AsTheSourceWroteIt(written);

        Assert.True(stage.IsClosed);
        Assert.Null(stage.Reached);
    }

    [Theory]
    [InlineData("  negociação  ")]
    [InlineData("NEGOCIAÇÃO")]
    public void EspacoENaoCaixaNaoMudamALeitura(string written)
    {
        Assert.Equal(PipelineStage.Negotiation, CommercialStage.AsTheSourceWroteIt(written).Reached);
    }

    [Theory]
    [InlineData("Em negociação avançada", PipelineStage.Negotiation)]
    [InlineData("Proposta em elaboração", PipelineStage.DraftingProposal)]
    [InlineData("Aguardando amostra do cliente", PipelineStage.AwaitingSample)]
    public void RotuloDesconhecidoCaiPeloRadical(string written, PipelineStage expected)
    {
        Assert.Equal(expected, CommercialStage.AsTheSourceWroteIt(written).Reached);
    }

    /// <summary>
    /// "Fechado recusado" casa com o radical de encerramento e tambem com o de recebimento.
    /// Trocar a ordem das duas linhas promoveria um orcamento recusado a recebido, e nenhum
    /// teste de rotulo conhecido veria a troca.
    /// </summary>
    [Fact]
    public void RadicalDeEncerramentoEConferidoAntesDoDeRecebimento()
    {
        Assert.True(CommercialStage.AsTheSourceWroteIt("Fechado recusado pela área").IsClosed);
    }

    [Theory]
    [InlineData("")]
    [InlineData("   ")]
    [InlineData(null)]
    [InlineData("etapa que ninguém declarou")]
    public void TextoQueNinguemReconheceComecaNoDegrauMaisBaixo(string? written)
    {
        Assert.Equal(PipelineStage.Qualification, CommercialStage.AsTheSourceWroteIt(written).Reached);
    }
}
