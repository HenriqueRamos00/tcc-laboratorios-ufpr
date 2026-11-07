using Portal.Domain.Pipeline;

namespace Portal.Domain.Quotes;

/// <summary>
/// A partir de que degrau existe proposta escrita, e portanto documento a servir.
///
/// E o par de <c>FIRST_STAGE_WITH_A_PROPOSAL</c> e <c>hasProposal</c> de
/// <c>frontend/src/app/model/quote-presentation.ts</c>. Em qualificacao, em analise pela
/// area e elaborando proposta o pedido ainda esta sendo entendido e precificado: nao existe
/// orcamento fechado, e um PDF montado ali afirmaria ao cliente um valor que ninguem
/// aprovou.
///
/// Solicitacao encerrada mantem a proposta, porque ela so fecha depois que houve uma: o
/// cliente que recusou continua com direito de reler o que recusou.
///
/// A regra mora no dominio e nao no adaptador de proposito. Ela vale para as tres fontes de
/// orcamento, e escrita dentro de uma delas as outras duas serviriam documento de orcamento
/// que ninguem terminou de escrever.
/// </summary>
public static class ProposalAvailability
{
    public const PipelineStage FirstStageWithAProposal = PipelineStage.Negotiation;

    public static bool ExistsAt(CommercialStage stage)
    {
        ArgumentNullException.ThrowIfNull(stage);

        if (stage.IsClosed) return true;

        return stage.Reached is PipelineStage reached
            && PipelineLadder.StepOf(reached) >= PipelineLadder.StepOf(FirstStageWithAProposal);
    }
}
