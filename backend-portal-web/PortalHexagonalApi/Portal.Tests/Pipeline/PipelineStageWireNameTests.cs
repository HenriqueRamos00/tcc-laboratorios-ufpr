using Portal.Api.Pipeline;
using Portal.Domain.Pipeline;

using Xunit;

namespace Portal.Tests.Pipeline;

/// <summary>
/// As chaves que viajam no corpo sao contrato com a tela, e nao escolha de serializador.
/// Chave errada nao da erro em lugar nenhum: a tela imprime vazio e a suite fica verde.
/// </summary>
public sealed class PipelineStageWireNameTests
{
    [Fact]
    public void EveryStageOfTheLadderHasItsOwnWireName()
    {
        string?[] names = [.. PipelineLadder.InOrder.Select(stage => PipelineStageWireName.Of(stage))];

        Assert.All(names, name => Assert.False(string.IsNullOrWhiteSpace(name)));
        Assert.Equal(PipelineLadder.InOrder.Count, names.Distinct(StringComparer.Ordinal).Count());
    }

    [Fact]
    public void NoStageTravelsAsNoName()
    {
        Assert.Null(PipelineStageWireName.Of(null));
    }

    /// <summary>
    /// Fixado contra frontend/src/app/model/analysis-pipeline.ts, letra por letra. A
    /// alternativa, deixar o conversor global de enum derivar o nome, produz in-progress e
    /// report-published.
    /// </summary>
    [Theory]
    [InlineData(PipelineStage.Qualification, "qualificacao")]
    [InlineData(PipelineStage.AreaAnalysis, "analise-da-area")]
    [InlineData(PipelineStage.DraftingProposal, "elaborando-proposta")]
    [InlineData(PipelineStage.Negotiation, "negociacao")]
    [InlineData(PipelineStage.ApprovedByClient, "aprovado-pelo-cliente")]
    [InlineData(PipelineStage.AwaitingSample, "aguardando-amostra")]
    [InlineData(PipelineStage.Received, "recebido")]
    [InlineData(PipelineStage.ProtocolAssigned, "protocolado")]
    [InlineData(PipelineStage.InProgress, "em-execucao")]
    [InlineData(PipelineStage.Complete, "completo")]
    [InlineData(PipelineStage.Validated, "validado")]
    [InlineData(PipelineStage.Finalized, "finalizado")]
    [InlineData(PipelineStage.DraftingReport, "elaborando-relatorio")]
    [InlineData(PipelineStage.ReportPublished, "relatorio-publicado")]
    public void TheWireNamesAreTheKeysTheScreenAlreadyUses(PipelineStage stage, string expected)
    {
        Assert.Equal(expected, PipelineStageWireName.Of(stage));
    }
}
