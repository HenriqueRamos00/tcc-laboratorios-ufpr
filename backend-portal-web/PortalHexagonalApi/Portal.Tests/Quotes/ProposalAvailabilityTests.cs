using Portal.Domain.Pipeline;
using Portal.Domain.Quotes;

using Xunit;

namespace Portal.Tests.Quotes;

/// <summary>
/// A regra do dono: o orcamento so existe para o cliente depois que alguem terminou de
/// escrever a proposta. A fronteira esta na negociacao, e os degraus em volta dela sao os que
/// importam.
/// </summary>
public sealed class ProposalAvailabilityTests
{
    [Theory]
    [InlineData(PipelineStage.Qualification)]
    [InlineData(PipelineStage.AreaAnalysis)]
    [InlineData(PipelineStage.DraftingProposal)]
    public void AntesDaNegociacaoNaoExisteProposta(PipelineStage stage)
    {
        Assert.False(ProposalAvailability.ExistsAt(CommercialStage.At(stage)));
    }

    [Theory]
    [InlineData(PipelineStage.Negotiation)]
    [InlineData(PipelineStage.ApprovedByClient)]
    [InlineData(PipelineStage.ReportPublished)]
    public void DaNegociacaoEmDianteExisteProposta(PipelineStage stage)
    {
        Assert.True(ProposalAvailability.ExistsAt(CommercialStage.At(stage)));
    }

    [Fact]
    public void SolicitacaoEncerradaMantemAProposta()
    {
        Assert.True(ProposalAvailability.ExistsAt(CommercialStage.Closed));
    }

    /// <summary>
    /// A escada e compartilhada com o frontend, e o degrau que abre o download e o quarto
    /// dela. Reordenar a escada muda a regra sem tocar nesta classe.
    /// </summary>
    [Fact]
    public void ADecisaoUsaAEscadaCompartilhadaENaoUmNumeroEscritoAMao()
    {
        Assert.Equal(
            PipelineLadder.StepOf(PipelineStage.Negotiation),
            PipelineLadder.StepOf(ProposalAvailability.FirstStageWithAProposal));
    }
}
