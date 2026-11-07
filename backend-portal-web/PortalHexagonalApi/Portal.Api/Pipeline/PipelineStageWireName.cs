using Portal.Domain.Pipeline;

namespace Portal.Api.Pipeline;

/// <summary>
/// A traducao do degrau para a chave que a tela ja consome. Mora no adaptador condutor
/// porque o corpo HTTP e assunto dele, e nao do dominio: o dominio nao sabe que existe JSON.
///
/// As chaves sao as de <c>PipelineStage</c> em
/// <c>frontend/src/app/model/analysis-pipeline.ts</c>, letra por letra. Elas estao em
/// portugues porque sao conteudo de dado e sao o contrato que a tela, os rotulos e as
/// classes de estilo dela ja usam; o identificador do lado do C# continua em ingles.
///
/// A tabela e explicita, e nao derivada do conversor global de enum em kebab-case, porque o
/// conversor produziria <c>in-progress</c> e <c>report-published</c>. A tela nao acusaria
/// erro: ela imprimiria vazio, que e o jeito mais caro de descobrir que o contrato quebrou.
/// </summary>
public static class PipelineStageWireName
{
    private static readonly Dictionary<PipelineStage, string> ByStage = new()
    {
        [PipelineStage.Qualification] = "qualificacao",
        [PipelineStage.AreaAnalysis] = "analise-da-area",
        [PipelineStage.DraftingProposal] = "elaborando-proposta",
        [PipelineStage.Negotiation] = "negociacao",
        [PipelineStage.ApprovedByClient] = "aprovado-pelo-cliente",
        [PipelineStage.AwaitingSample] = "aguardando-amostra",
        [PipelineStage.Received] = "recebido",
        [PipelineStage.ProtocolAssigned] = "protocolado",
        [PipelineStage.InProgress] = "em-execucao",
        [PipelineStage.Complete] = "completo",
        [PipelineStage.Validated] = "validado",
        [PipelineStage.Finalized] = "finalizado",
        [PipelineStage.DraftingReport] = "elaborando-relatorio",
        [PipelineStage.ReportPublished] = "relatorio-publicado"
    };

    /// <summary>
    /// Ausencia de etapa viaja como nulo, e nao como texto vazio nem como o primeiro
    /// degrau: nulo quer dizer que esta fonte nao tem nada a dizer e que a tela deve usar a
    /// etapa do lado comercial, e qualificacao queria dizer que o pedido esta comecando.
    /// </summary>
    public static string? Of(PipelineStage? stage)
    {
        return stage is null ? null : ByStage[stage.Value];
    }
}
