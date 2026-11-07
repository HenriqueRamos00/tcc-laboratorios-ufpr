using Portal.Domain.Pipeline;
using Portal.Domain.Pipeline.Exceptions;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// A ordem da escada e o que a regra do degrau mais alto usa para decidir. Ela e dado
/// declarado, e dado declarado que ninguem confere sai de sincronia com a tela sem erro de
/// compilacao em lugar nenhum.
/// </summary>
public sealed class PipelineLadderTests
{
    [Fact]
    public void EveryStageOfThePipelineAppearsExactlyOnceInTheLadder()
    {
        PipelineStage[] declared = Enum.GetValues<PipelineStage>();

        Assert.Equal(declared.Length, PipelineLadder.InOrder.Count);
        Assert.Equal(declared.Length, PipelineLadder.InOrder.Distinct().Count());
        Assert.All(declared, stage => Assert.Contains(stage, PipelineLadder.InOrder));
    }

    [Fact]
    public void TheStepsGrowFromTheFirstStageToTheLast()
    {
        int[] steps = [.. PipelineLadder.InOrder.Select(PipelineLadder.StepOf)];

        Assert.Equal(0, steps[0]);
        Assert.Equal(PipelineLadder.InOrder.Count - 1, steps[^1]);
        Assert.Equal([.. steps.Order()], steps);
    }

    /// <summary>
    /// As duas pontas e a fronteira entre a metade comercial e a metade do laboratorio, que
    /// e onde esta fatia encosta na outra.
    /// </summary>
    [Fact]
    public void TheLaboratoryHalfOfTheLadderComesAfterTheCommercialHalf()
    {
        Assert.Equal(PipelineStage.Qualification, PipelineLadder.InOrder[0]);
        Assert.Equal(PipelineStage.ReportPublished, PipelineLadder.InOrder[^1]);
        Assert.True(PipelineLadder.StepOf(PipelineStage.Received)
            > PipelineLadder.StepOf(PipelineStage.AwaitingSample));
        Assert.True(PipelineLadder.StepOf(PipelineStage.DraftingReport)
            > PipelineLadder.StepOf(PipelineStage.Finalized));
    }

    [Fact]
    public void AStageOutsideTheDeclaredOrderIsRefusedByName()
    {
        Assert.Throws<StageOutsideTheLadderException>(() => PipelineLadder.StepOf((PipelineStage)99));
    }

    [Fact]
    public void NoStageIsHighestInAnEmptyCollection()
    {
        Assert.Null(PipelineLadder.Highest([]));
        Assert.Null(PipelineLadder.LeastAdvanced([]));
    }

    [Fact]
    public void TheOnlyStageOfACollectionOfOneIsBothHighestAndLeastAdvanced()
    {
        Assert.Equal(PipelineStage.Validated, PipelineLadder.Highest([PipelineStage.Validated]));
        Assert.Equal(PipelineStage.Validated, PipelineLadder.LeastAdvanced([PipelineStage.Validated]));
    }

    /// <summary>
    /// A ordem de chegada nao decide nada. Tomar o primeiro ou o ultimo da colecao acerta
    /// por acidente em metade das entradas, e e assim que esse defeito sobrevive.
    /// </summary>
    [Fact]
    public void TheHighestStageDoesNotDependOnTheOrderTheStagesArrive()
    {
        Assert.Equal(
            PipelineStage.ReportPublished,
            PipelineLadder.Highest([PipelineStage.ReportPublished, PipelineStage.InProgress]));
        Assert.Equal(
            PipelineStage.ReportPublished,
            PipelineLadder.Highest([PipelineStage.InProgress, PipelineStage.ReportPublished]));
    }

    [Fact]
    public void TheLeastAdvancedStageDoesNotDependOnTheOrderTheStagesArrive()
    {
        Assert.Equal(
            PipelineStage.InProgress,
            PipelineLadder.LeastAdvanced([PipelineStage.ReportPublished, PipelineStage.InProgress]));
        Assert.Equal(
            PipelineStage.InProgress,
            PipelineLadder.LeastAdvanced([PipelineStage.InProgress, PipelineStage.ReportPublished]));
    }

    [Fact]
    public void AllStagesBeingEqualIsStillThatStage()
    {
        PipelineStage[] repeated = [PipelineStage.Finalized, PipelineStage.Finalized, PipelineStage.Finalized];

        Assert.Equal(PipelineStage.Finalized, PipelineLadder.Highest(repeated));
        Assert.Equal(PipelineStage.Finalized, PipelineLadder.LeastAdvanced(repeated));
    }
}
