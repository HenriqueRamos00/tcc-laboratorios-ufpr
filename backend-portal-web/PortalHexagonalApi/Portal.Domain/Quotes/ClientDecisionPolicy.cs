using Portal.Domain.Pipeline;

namespace Portal.Domain.Quotes;

public static class ClientDecisionPolicy
{
    public static PipelineStage? StageAfter(ClientDecisionKind kind) => kind switch
    {
        ClientDecisionKind.Acceptance or ClientDecisionKind.PurchaseOrder => PipelineStage.ApprovedByClient,
        ClientDecisionKind.SampleShipment => PipelineStage.AwaitingSample,
        _ => null
    };

    public static bool Allows(
        ClientDecisionKind kind,
        CommercialStage commercial,
        PipelineStage? laboratory,
        IReadOnlyCollection<ClientDecision> recorded)
    {
        ArgumentNullException.ThrowIfNull(commercial);
        ArgumentNullException.ThrowIfNull(recorded);

        if (commercial.IsClosed || recorded.Any(decision => decision.Kind == ClientDecisionKind.Rejection))
            return false;

        PipelineStage? reached = PipelineLadder.Highest(
            new[] { commercial.Reached, laboratory }
                .Concat(recorded.Select(decision => StageAfter(decision.Kind)))
                .OfType<PipelineStage>());

        if (reached is not PipelineStage stage) return false;

        return kind == ClientDecisionKind.SampleShipment
            ? stage == PipelineStage.ApprovedByClient
            : ProposalAvailability.ExistsAt(CommercialStage.At(stage))
                && PipelineLadder.StepOf(stage) < PipelineLadder.StepOf(PipelineStage.ApprovedByClient);
    }

    public static ClientDecision? Latest(IEnumerable<ClientDecision> recorded)
    {
        ArgumentNullException.ThrowIfNull(recorded);

        return recorded.OrderByDescending(decision => decision.RecordedAt).FirstOrDefault();
    }
}
