using Portal.Domain.Pipeline;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// As duas regras do dominio, sem fonte nenhuma do outro lado: a porteira do recebimento e
/// a direcao da dobra. Nenhum destes testes conhece o numero de uma coluna do AutoLAB, e e
/// assim que se ve que a regra nao depende da fonte.
/// </summary>
public sealed class ReachedStageTests
{
    [Fact]
    public void ASampleThatDidNotEnterTheLaboratoryHasNoStage()
    {
        Assert.Null(ReachedStage.OfOneSample(false, [PipelineStage.Complete]));
    }

    /// <summary>
    /// E a linha 2025_41689 das 3345, lancada errada na origem: ensaio completo sem
    /// recebimento. Ela nao tem tratamento proprio, nao e corrigida e nao e acusada: ela
    /// para na porteira junto com as 774 que simplesmente nao chegaram.
    /// </summary>
    [Fact]
    public void AnEnsaioReportedWithoutReceivingIsNotPromotedNorRepaired()
    {
        Assert.Null(ReachedStage.OfOneSample(
            false,
            [PipelineStage.Complete, PipelineStage.ReportPublished]));
    }

    [Fact]
    public void ASampleThatEnteredTheLaboratoryIsAtLeastReceived()
    {
        Assert.Equal(PipelineStage.Received, ReachedStage.OfOneSample(true, []));
    }

    /// <summary>
    /// O exemplo do dono: finalizado com relatorio em elaboracao sobe para elaborando
    /// relatorio, sem que o ensaio mude de pe.
    /// </summary>
    [Fact]
    public void TheStageIsTheHighestStepTheFieldsJustify()
    {
        Assert.Equal(
            PipelineStage.DraftingReport,
            ReachedStage.OfOneSample(true, [PipelineStage.Finalized, PipelineStage.DraftingReport]));
    }

    [Fact]
    public void AQuoteWithoutAnySampleHasNoStage()
    {
        Assert.Null(ReachedStage.OfAQuote([]));
    }

    [Fact]
    public void AQuoteWithOneSampleIsAtTheStageOfThatSample()
    {
        Assert.Equal(PipelineStage.Validated, ReachedStage.OfAQuote([PipelineStage.Validated]));
    }

    /// <summary>
    /// Sao os 46 codigos de orcamento que repetem com estados diferentes. OrcamentoCRM nao e
    /// chave e a coluna que identificaria a amostra nao existe nesta consulta, entao o
    /// resumo honesto e a amostra menos avancada.
    /// </summary>
    [Fact]
    public void AQuoteIsSummarizedByItsLeastAdvancedSample()
    {
        Assert.Equal(
            PipelineStage.InProgress,
            ReachedStage.OfAQuote([PipelineStage.ReportPublished, PipelineStage.InProgress]));
    }

    /// <summary>
    /// O caso que a amostra real produz: uma linha 1|1|3|2 ao lado de uma 0|0||0. A amostra
    /// menos avancada nem entrou no laboratorio, entao o laboratorio nao tem nada a dizer
    /// sobre o orcamento.
    ///
    /// A implementacao tentadora e descartar as ausencias antes de dobrar. Ela devolve
    /// "relatorio publicado" para um orcamento que tem amostra ainda no comercial, e e a
    /// unica mutacao desta fatia que chegaria em producao parecendo certa.
    /// </summary>
    [Fact]
    public void AQuoteWithOneSampleOutsideTheLaboratoryIsNotPromoted()
    {
        Assert.Null(ReachedStage.OfAQuote([PipelineStage.ReportPublished, null]));
        Assert.Null(ReachedStage.OfAQuote([null, PipelineStage.ReportPublished]));
    }

    [Fact]
    public void AQuoteWhoseEverySampleIsOutsideTheLaboratoryHasNoStage()
    {
        Assert.Null(ReachedStage.OfAQuote([null, null]));
    }
}
