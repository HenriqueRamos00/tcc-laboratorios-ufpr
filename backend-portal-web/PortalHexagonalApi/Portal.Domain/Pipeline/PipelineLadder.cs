using Portal.Domain.Pipeline.Exceptions;

namespace Portal.Domain.Pipeline;

/// <summary>
/// A ordem dos degraus, num lugar so. E o par de
/// <c>PIPELINE_STAGES</c> e <c>stageIndex</c> do frontend.
///
/// A ordem fica numa lista declarada, e nao no valor inteiro implicito do enum, porque o
/// valor implicito muda em silencio quando alguem reordena a declaracao: a comparacao
/// passaria a dar outro resultado sem que nenhuma linha de regra tivesse sido tocada. Aqui
/// reordenar a lista e uma mudanca visivel, e o teste que fixa as duas pontas da escada
/// fica vermelho.
/// </summary>
public static class PipelineLadder
{
    public static readonly IReadOnlyList<PipelineStage> InOrder =
    [
        PipelineStage.Qualification,
        PipelineStage.AreaAnalysis,
        PipelineStage.DraftingProposal,
        PipelineStage.Negotiation,
        PipelineStage.ApprovedByClient,
        PipelineStage.AwaitingSample,
        PipelineStage.Received,
        PipelineStage.ProtocolAssigned,
        PipelineStage.InProgress,
        PipelineStage.Complete,
        PipelineStage.Validated,
        PipelineStage.Finalized,
        PipelineStage.DraftingReport,
        PipelineStage.ReportPublished
    ];

    private static readonly Dictionary<PipelineStage, int> StepByStage = InOrder
        .Select((stage, step) => (stage, step))
        .ToDictionary(pair => pair.stage, pair => pair.step);

    /// <summary>
    /// Em que altura da escada o degrau esta. Degrau fora da escada e defeito de quem
    /// declarou o enum sem declarar a ordem, e nao dado ruim da fonte: falha com nome
    /// proprio para quem le o erro saber que o conserto e em <see cref="InOrder"/>.
    /// </summary>
    public static int StepOf(PipelineStage stage)
    {
        return StepByStage.TryGetValue(stage, out int step)
            ? step
            : throw new StageOutsideTheLadderException(stage);
    }

    /// <summary>
    /// O degrau mais alto da colecao, que e a regra do dono: a etapa final e o degrau mais
    /// alto que os campos justificam. Colecao vazia nao tem degrau mais alto, e devolver o
    /// primeiro degrau ali afirmaria qualificacao a quem nao disse nada.
    /// </summary>
    public static PipelineStage? Highest(IEnumerable<PipelineStage> stages)
    {
        ArgumentNullException.ThrowIfNull(stages);

        List<PipelineStage> received = [.. stages];

        if (received.Count == 0) return null;

        return received.MaxBy(StepOf);
    }

    /// <summary>
    /// O degrau mais baixo da colecao. Existe para resumir um orcamento cujas amostras
    /// estao em pes diferentes, e e o oposto de <see cref="Highest"/> de proposito: dentro
    /// de uma amostra o dado mais avancado manda, entre amostras o menos avancado manda.
    /// </summary>
    public static PipelineStage? LeastAdvanced(IEnumerable<PipelineStage> stages)
    {
        ArgumentNullException.ThrowIfNull(stages);

        List<PipelineStage> received = [.. stages];

        if (received.Count == 0) return null;

        return received.MinBy(StepOf);
    }
}
